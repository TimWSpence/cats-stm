package org.timwspence.cats.stm

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

import cats.effect.Async
import org.timwspence.cats.stm.STM.internal._

import scala.collection.mutable.{Map => MMap}

case class STM[A](run: TLog => TResult[A]) extends AnyVal {

  def map[B](f: A => B): STM[B] = STM { log =>
    run(log) match {
      case TSuccess(value) => TSuccess(f(value))
      case TFailure(error) => TFailure(error) //Coercion would be nice here!
      case TRetry          => TRetry          //And here!
    }
  }

  def flatMap[B](f: A => STM[B]): STM[B] = STM { log =>
    run(log) match {
      case TSuccess(value) => f(value).run(log)
      case TFailure(error) => TFailure(error)
      case TRetry          => TRetry
    }
  }

  def commit[F[_]: Async]: F[A] = STM.atomically[F](this)

}

object STM {

  def atomically[F[_]] = new AtomicallyPartiallyApplied[F]

  val retry: STM[Unit] = STM { _ => TRetry }

  def orElse[A](attempt: STM[A], fallback: STM[A]): STM[A] = STM { log =>
    attempt.run(log) match {
      case TRetry => fallback.run(log)
      case r      => r
    }
  }

  def check(check: => Boolean): STM[Unit] = if (check) unit else retry

  def pure[A](a: A): STM[A] = STM { _ => TSuccess(a) }

  val unit: STM[Unit] = pure(())

  class AtomicallyPartiallyApplied[F[_]] {
    def apply[A](stm: STM[A])(implicit F: Async[F]): F[A] = F.async { cb =>
      val txId = IdGen.incrementAndGet

      def attempt: () => Unit = () => {
        var result: Either[Throwable, A] = null
        var success = false
        val log = MMap[Long, TLogEntry]()
        globalLock.acquire
        try {
          stm.run(log) match {
            case TSuccess(value) => {
              for(entry <- log.values) {
                entry.commit
              }
              result = Right(value)
              success = true
            }
            case TFailure(error) => result = Left(error)
            case TRetry          => registerPending(txId, attempt, log)
          }
        }
        finally globalLock.release
        if (success) rerunPending(txId, log) //should this be before the cb invocation?
        if (result != null) cb(result)
      }

      attempt()

    }

    private def registerPending(txId: Long, pending: () => Unit, log: TLog): Unit = {
      for(entry <- log.values) {
        entry.tvar.pending.updateAndGet(m => m + (txId -> pending))
      }
    }

    private def rerunPending(txId: Long, log: TLog): Unit = {
      val todo = MMap[Long, Pending]()
      for(entry <- log.values) {
        val updated = entry.tvar.pending.updateAndGet(_ - txId)
        todo ++= updated
      }
      for(pending <- todo.values) {
        pending()
      }
    }
  }


  private[stm] object internal {

    type TLog = MMap[Long, TLogEntry]
    type Pending = () => Unit

    abstract class TLogEntry{
      type Repr
      var current: Repr
      val tvar: TVar[Repr]

      def unsafeGet[A]: A = current.asInstanceOf[A]

      def unsafeSet[A](a: A): Unit = current = a.asInstanceOf[Repr]

      def commit: Unit = tvar.value = current
    }

    object TLogEntry {

      def apply[A](tvar0: TVar[A], current0: A): TLogEntry = new TLogEntry {
        override type Repr = A
        override var current: A = current0
        override val tvar: TVar[A] = tvar0
      }

    }

    sealed trait TResult[+A]
    case class TSuccess[A](value: A) extends TResult[A]
    case class TFailure[A](error: Throwable) extends TResult[A]
    case object TRetry extends TResult[Nothing]

    val IdGen = new AtomicLong()

    val globalLock = new Semaphore(1, true)

  }

}
