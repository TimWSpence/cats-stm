package io.github.timwspence.cats.stm

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

import cats.effect.Async
import cats.{Monad, Monoid}
import STM.internal._

import scala.annotation.tailrec
import scala.collection.mutable.{Map => MMap}
import scala.compat.java8.FunctionConverters._

/**
  * Monad representing transactions involving one or more
  * `TVar`s.
  */
case class STM[A](run: TLog => TResult[A]) extends AnyVal {

  /**
    * Functor map on `STM`.
    */
  final def map[B](f: A => B): STM[B] = STM { log =>
    run(log) match {
      case TSuccess(value) => TSuccess(f(value))
      case e @ TFailure(_) => e
      case TRetry          => TRetry
    }
  }

  /**
    * Monadic bind on `STM`.
    */
  final def flatMap[B](f: A => STM[B]): STM[B] = STM { log =>
    run(log) match {
      case TSuccess(value) => f(value).run(log)
      case e @ TFailure(_) => e
      case TRetry          => TRetry
    }
  }

  /**
    * Try an alternative `STM` action if this one retries.
    */
  final def orElse(fallback: STM[A]): STM[A] = STM { log => {
    val revert = log.snapshot
    run(log) match {
      case TRetry => {revert(); fallback.run(log)}
      case r => r
    }
  }
  }

  /**
    * Commit this `STM` action as an `IO` action. The mutable
    * state of `TVar`s is only modified when this is invoked
    * (hence the `IO` context - modifying mutable state
    * is a side effect).
    */
  final def commit[F[_]: Async]: F[A] = STM.atomically[F](this)

}

object STM {

  /**
    * Commit the `STM` action as an `IO` action. The mutable
    * state of `TVar`s is only modified when this is invoked
    * (hence the `IO` context - modifying mutable state
    * is a side effect).
    */
  def atomically[F[_]] = new AtomicallyPartiallyApplied[F]

  /**
    * Convenience definition.
    */
  def retry[A]: STM[A] = STM { _ =>
    TRetry
  }

  /**
    * Fallback to an alternative `STM` action if the first one
    * retries. The whole `orElse` action is retried if both
    * {@code attempt} and {@code fallback} retry.
    */
  def orElse[A](attempt: STM[A], fallback: STM[A]): STM[A] = attempt.orElse(fallback)

  /**
    * Retry transaction until {@code check} succeeds.
    */
  def check(check: => Boolean): STM[Unit] = if (check) unit else retry

  /**
    * Abort a transaction. Will raise {@code error} whenever
    * evaluated with [[atomically]].
    */
  def abort[A](error: Throwable): STM[A] = STM { _ =>
    TFailure(error)
  }

  /**
    * Monadic return.
    */
  def pure[A](a: A): STM[A] = STM { _ =>
    TSuccess(a)
  }

  /**
    * Alias for `pure(())`.
    */
  val unit: STM[Unit] = pure(())

  implicit val stmMonad: Monad[STM] = new Monad[STM] {
    override def flatMap[A, B](fa: STM[A])(f: A => STM[B]): STM[B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => STM[Either[A, B]]): STM[B] = STM { log =>
      @tailrec
      def step(a: A): TResult[B] = f(a).run(log) match {
        case TSuccess(Left(a1)) => step(a1)
        case TSuccess(Right(b)) => TSuccess(b)
        case e @ TFailure(_)    => e
        case TRetry             => TRetry
      }

      step(a)
    }

    override def pure[A](x: A): STM[A] = STM.pure(x)
  }

  implicit def stmMonoid[A](implicit M: Monoid[A]): Monoid[STM[A]] = new Monoid[STM[A]] {
    override def empty: STM[A] = STM.pure(M.empty)

    override def combine(x: STM[A], y: STM[A]): STM[A] = STM { log =>
      x.run(log) match {
        case TSuccess(value1) =>
          y.run(log) match {
            case TSuccess(value2) => TSuccess(M.combine(value1, value2))
            case r                => r
          }
        case r                => r
      }
    }
  }

  class AtomicallyPartiallyApplied[F[_]] {
    def apply[A](stm: STM[A])(implicit F: Async[F]): F[A] = F.async { cb =>
      val txId = IdGen.incrementAndGet

      def attempt: () => Unit = () => {
        var result: Either[Throwable, A] = null
        var success                      = false
        val log                          = TLog(MMap[Long, TLogEntry]())
        globalLock.acquire
        try {
          stm.run(log) match {
            case TSuccess(value) => {
              for (entry <- log.values) {
                entry.commit
              }
              result = Right(value)
              success = true
            }
            case TFailure(error) => result = Left(error)
            case TRetry          => registerPending(txId, attempt, log)
          }
        } catch {
          case e: Throwable => result = Left(e)
        } finally globalLock.release
        if (success) rerunPending(txId, log)
        if (result != null) cb(result)
      }

      attempt()

    }

    private def registerPending(txId: Long, pending: () => Unit, log: TLog): Unit =
      for (entry <- log.values) {
        entry.tvar.pending.updateAndGet(asJavaUnaryOperator(m => m + (txId -> pending)))
      }

    private def rerunPending(txId: Long, log: TLog): Unit = {
      val todo = MMap[Long, Pending]()
      for (entry <- log.values) {
        val updated = entry.tvar.pending.updateAndGet(asJavaUnaryOperator(_ - txId))
        todo ++= updated
      }
      for (pending <- todo.values) {
        pending()
      }
    }
  }

  private[stm] object internal {

    case class TLog(val map: MMap[Long, TLogEntry]) {
      def apply(id: Long): TLogEntry = map(id)

      def values: Iterable[TLogEntry] = map.values

      def contains(id: Long): Boolean = map.contains(id)

      def +=(pair: (Long, TLogEntry)) = map += pair

      //Returns a callback to revert the entries
      //currently contained in the map
      def snapshot: () => Unit = {
        val currentEntries = map.values
        currentEntries.map(_.snapshot)
        () => currentEntries.map(_.revert)
      }

    }

    type Pending = () => Unit

    abstract class TLogEntry {
      type Repr
      var current: Repr
      var versions: List[Repr]
      val tvar: TVar[Repr]

      def unsafeGet[A]: A = current.asInstanceOf[A]

      def unsafeSet[A](a: A): Unit = current = a.asInstanceOf[Repr]

      def commit: Unit = tvar.value = current

      def snapshot: Unit = versions = current :: versions

      def revert: Unit = {
        current = versions.head
        versions = versions.tail
      }
    }

    object TLogEntry {

      def apply[A](tvar0: TVar[A], current0: A): TLogEntry = new TLogEntry {
        override type Repr = A
        override var current: A    = current0
        override var versions: List[A] = List(current0)
        override val tvar: TVar[A] = tvar0
      }

    }

    sealed trait TResult[+A]
    case class TSuccess[A](value: A)      extends TResult[A]
    case class TFailure(error: Throwable) extends TResult[Nothing]
    case object TRetry                    extends TResult[Nothing]

    val IdGen = new AtomicLong()

    val globalLock = new Semaphore(1, true)

  }

}
