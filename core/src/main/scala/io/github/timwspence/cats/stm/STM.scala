package io.github.timwspence.cats.stm

import java.util.concurrent.atomic.AtomicLong

import cats.effect.Async
import cats.{Monad, Monoid, MonoidK}

import io.github.timwspence.cats.stm.STM.internal._

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable.{Map => MMap}
import scala.compat.java8.FunctionConverters._

/**
  * Monad representing transactions involving one or more
  * `TVar`s.
  *
  * This design was inspired by [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf) and informed by ZIO
  * which has a common origin in that paper via the [stm package](http://hackage.haskell.org/package/stm).
  */
final class STM[A] private[stm] (private[stm] val run: TLog => TResult[A]) extends AnyVal {

  /**
    * Functor map on `STM`.
    */
  final def map[B](f: A => B): STM[B] =
    STM { log =>
      run(log) match {
        case TSuccess(value) => TSuccess(f(value))
        case e @ TFailure(_) => e
        case TRetry          => TRetry
      }
    }

  /**
    * Monadic bind on `STM`.
    */
  final def flatMap[B](f: A => STM[B]): STM[B] =
    STM { log =>
      run(log) match {
        case TSuccess(value) => f(value).run(log)
        case e @ TFailure(_) => e
        case TRetry          => TRetry
      }
    }

  /**
    * Try an alternative `STM` action if this one retries.
    */
  final def orElse(fallback: STM[A]): STM[A] =
    STM { log =>
      val revert = log.snapshot
      run(log) match {
        case TRetry => revert(); fallback.run(log)
        case r      => r
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

  private[stm] def apply[A](run: TLog => TResult[A]): STM[A] =
    new STM[A](run)

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
  def retry[A]: STM[A] = STM(_ => TRetry)

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
  def abort[A](error: Throwable): STM[A] = STM(_ => TFailure(error))

  /**
    * Monadic return.
    */
  def pure[A](a: A): STM[A] = STM(_ => TSuccess(a))

  /**
    * Alias for `pure(())`.
    */
  val unit: STM[Unit] = pure(())

  implicit val stmMonad: Monad[STM] with MonoidK[STM] = new Monad[STM] with MonoidK[STM] {
    override def flatMap[A, B](fa: STM[A])(f: A => STM[B]): STM[B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => STM[Either[A, B]]): STM[B] =
      STM { log =>
        @tailrec
        def step(a: A): TResult[B] =
          f(a).run(log) match {
            case TSuccess(Left(a1)) => step(a1)
            case TSuccess(Right(b)) => TSuccess(b)
            case e @ TFailure(_)    => e
            case TRetry             => TRetry
          }

        step(a)
      }

    override def pure[A](x: A): STM[A] = STM.pure(x)

    override def empty[A]: STM[A] = STM.retry

    override def combineK[A](x: STM[A], y: STM[A]): STM[A] = x.orElse(y)
  }

  implicit def stmMonoid[A](implicit M: Monoid[A]): Monoid[STM[A]] =
    new Monoid[STM[A]] {
      override def empty: STM[A] = STM.pure(M.empty)

      override def combine(x: STM[A], y: STM[A]): STM[A] =
        STM { log =>
          x.run(log) match {
            case TSuccess(value1) =>
              y.run(log) match {
                case TSuccess(value2) => TSuccess(M.combine(value1, value2))
                case r                => r
              }
            case r => r
          }
        }
    }

  final class AtomicallyPartiallyApplied[F[_]] {
    def apply[A](stm: STM[A])(implicit F: Async[F]): F[A] =
      F.async { (cb: (Either[Throwable, A] => Unit)) =>
        def attempt: Pending =
          () => {
            val txId                         = IdGen.incrementAndGet
            var result: Either[Throwable, A] = null
            val log                          = TLog.empty
            STM.synchronized {
              try stm.run(log) match {
                case TSuccess(value) =>
                  for (entry <- log.values)
                    entry.commit()
                  result = Right(value)
                  collectPending(log)
                  rerunPending
                case TFailure(error) => result = Left(error)
                case TRetry          => registerPending(txId, attempt, log)
              } catch {
                case e: Throwable => result = Left(e)
              }
            }
            if (result != null) cb(result)
          }

        attempt()
      }

    private def registerPending(txId: TxId, pending: Pending, log: TLog): Unit = {
      //TODO could replace this with an onComplete callback instead of passing etvars everywhere
      val txn = Txn(txId, pending, log.values.map(entry => ETVar(entry.tvar)).toSet)
      for (entry <- log.values)
        entry.tvar.pending.updateAndGet(asJavaUnaryOperator(m => m + (txId -> txn)))
    }

    private def collectPending(log: TLog): Unit = {
      var pending = Map.empty[TxId, Txn]
      for (entry <- log.values) {
        val updated = entry.tvar.pending.getAndSet(Map())
        for ((k, v) <- updated) {
          for (e <- v.tvs)
            e.tv.pending.getAndUpdate(asJavaUnaryOperator(m => m - k))
          pending = pending + (k -> v)
        }
      }
      for (p <- pending.values)
        pendingQueue = pendingQueue.enqueue(p)

    }

    private def rerunPending(): Unit =
      while (!pendingQueue.isEmpty) {
        val (p, remaining) = pendingQueue.dequeue
        pendingQueue = remaining
        p.pending()
      }
  }

  private[stm] object internal {

    @volatile var pendingQueue: Queue[Txn] = Queue.empty

    case class TLog(val map: MMap[TxId, TLogEntry]) {
      def apply(id: TxId): TLogEntry = map(id)

      def values: Iterable[TLogEntry] = map.values

      def contains(id: TxId): Boolean = map.contains(id)

      def +=(pair: (TxId, TLogEntry)) = map += pair

      //Returns a callback to revert the log to the state at the
      //point when snapshot was invoked
      def snapshot: () => Unit = {
        val snapshot: Map[TxId, Any] = map.toMap.map {
          case (id, e) => id -> e.current
        }
        () =>
          for (pair <- map)
            if (snapshot contains (pair._1))
              //The entry was already modified at
              //some point in the transaction
              pair._2.unsafeSet(snapshot(pair._1))
            else
              //The entry was introduced in the attempted
              //part of the transaction that we are now
              //reverting so we reset to the initial
              //value.
              //We don't want to remove it from the map
              //as we still want to add the currently
              //executing transaction to the set of
              //pending transactions for this tvar if
              //the whole transaction fails.
              pair._2.reset()
      }

    }

    object TLog {
      def empty: TLog = TLog(MMap[TxId, TLogEntry]())
    }

    type Pending = () => Unit

    type TxId = Long

    case class Txn(id: TxId, pending: Pending, tvs: Set[ETVar])

    // Existential TVar
    abstract class ETVar {
      type Repr
      val tv: TVar[Repr]
    }

    object ETVar {
      def apply[A](t: TVar[A]): ETVar =
        new ETVar {
          override type Repr = A
          override val tv = t
        }
    }

    abstract class TLogEntry {
      type Repr
      var current: Repr
      val initial: Repr
      val tvar: TVar[Repr]

      def unsafeGet[A]: A = current.asInstanceOf[A]

      def unsafeSet[A](a: A): Unit = current = a.asInstanceOf[Repr]

      def commit(): Unit = tvar.value = current

      def reset(): Unit = current = initial

    }

    object TLogEntry {

      def apply[A](tvar0: TVar[A], current0: A): TLogEntry =
        new TLogEntry {
          override type Repr = A
          override var current: A    = current0
          override val initial: A    = tvar0.value
          override val tvar: TVar[A] = tvar0
        }

    }

    sealed trait TResult[+A]                    extends Product with Serializable
    final case class TSuccess[A](value: A)      extends TResult[A]
    final case class TFailure(error: Throwable) extends TResult[Nothing]
    case object TRetry                          extends TResult[Nothing]

    val IdGen = new AtomicLong()
  }

}
