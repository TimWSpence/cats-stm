package org.timwspence.cats.stm

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

import cats.effect.Sync
import org.timwspence.cats.stm.STM.internal._

import scala.collection.mutable.{Map => MMap}

case class STM[A](run: TLog => TResult[A]) extends AnyVal {

  def map[B](f: A => B): STM[B] = STM { log =>
    run(log) match {
      case TSuccess(value) => TSuccess(f(value))
      case TFailure(error) => TFailure(error) //Coercion would be nice here!
      case TRetry()        => TRetry()        //And here!
    }
  }

  def flatMap[B](f: A => STM[B]): STM[B] = STM { log =>
    run(log) match {
      case TSuccess(value) => f(value).run(log)
      case TFailure(error) => TFailure(error)
      case TRetry()        => TRetry()
    }
  }

}

object STM {

  def atomically[F[_]] = new AtomicallyPartiallyApplied[F]

  class AtomicallyPartiallyApplied[F[_]] {
    def apply[A](stm: STM[A])(implicit F: Sync[F]): F[A] = F.delay {
      internal.globalLock.acquire
      try {
        val log = MMap[Long, TLogEntry]()
        val result = stm.run(log) match {
          case TSuccess(value) => {
            for(entry <- log.values) {
              entry.commit
            }
            value
          }
          case TFailure(error) => throw error
          case TRetry()        => throw new RuntimeException("Need to handle retry")
        }
        result
      }
      finally internal.globalLock.release
    }
  }

  def retry: STM[Unit] = STM { _ => TRetry() }

  def orElse[A](attempt: STM[A], fallback: STM[A]): STM[A] = STM { log =>
    attempt.run(log) match {
      case TRetry() => fallback.run(log)
      case r        => r
    }
  }

  def check(check: => Boolean): STM[Unit] = if (check) unit else retry

  def unit: STM[Unit] = STM { _ => TSuccess(()) }

  private[stm] object internal {

    type TLog = MMap[Long, TLogEntry]

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

    sealed trait TResult[A]
    case class TSuccess[A](value: A) extends TResult[A]
    case class TFailure[A](error: Throwable) extends TResult[A]
    case class TRetry[A]() extends TResult[A]

    val TvarIdGen = new AtomicLong()

    val globalLock = new Semaphore(1, true)

  }


}
