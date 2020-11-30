/*
 * Copyright 2020 TimWSpence
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.timwspence.cats.stm

import scala.annotation.tailrec

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.implicits._
import cats.{Monad, Monoid, MonoidK, StackSafeMonad}

trait STM[F[_]] {
  import Internals._

  //TODO should we split this into public trait and private impl?
  class TVar[A] private[stm] (
    val id: TVarId,
    @volatile var value: A,
    val lock: Semaphore[F],
    val retries: Ref[F, List[Deferred[F, Unit]]]
  ) {
    def get: Txn[A] = Get(this)

    def modify(f: A => A): Txn[Unit] = Modify(this, f)

    def set(a: A): Txn[Unit] = modify(_ => a)

    private[stm] def registerRetry(signal: Deferred[F, Unit]): F[Unit] = retries.update(signal :: _)

  }

  object TVar {
    def of[A](a: A): Txn[TVar[A]] = Alloc(a)
  }

  sealed abstract class Txn[+A] {

    /**
      * Functor map on `STM`.
      */
    final def map[B](f: A => B): Txn[B] = Bind(this, f.andThen(Pure(_)))

    /**
      * Monadic bind on `STM`.
      */
    final def flatMap[B](f: A => Txn[B]): Txn[B] = Bind(this, f)

    /**
      * Try an alternative `STM` action if this one retries.
      */
    final def orElse[B >: A](other: Txn[B]): Txn[B] = OrElse(this, other)
  }

  object Txn {
    def pure[A](a: A): Txn[A] = Pure(a)

    def retry[A]: Txn[A] = Retry

    implicit val txnMonad: StackSafeMonad[Txn] with MonoidK[Txn] =
      new StackSafeMonad[Txn] with MonoidK[Txn] {
        override def flatMap[A, B](fa: Txn[A])(f: A => Txn[B]): Txn[B] = fa.flatMap(f)

        override def pure[A](x: A): Txn[A] = Txn.pure(x)

        override def empty[A]: Txn[A] = Txn.retry

        override def combineK[A](x: Txn[A], y: Txn[A]): Txn[A] = x.orElse(y)
      }

    implicit def stmMonoid[A](implicit M: Monoid[A]): Monoid[Txn[A]] =
      new Monoid[Txn[A]] {
        override def empty: Txn[A] = Txn.pure(M.empty)

        override def combine(x: Txn[A], y: Txn[A]): Txn[A] =
          for {
            l <- x
            r <- y
          } yield M.combine(l, r)
      }
  }

  def pure[A](a: A): Txn[A] = Txn.pure(a)

  def commit[A](txn: Txn[A]): F[A]

  def check(cond: Boolean): Txn[Unit] = if (cond) unit else retry

  def retry[A]: Txn[A] = Retry

  val unit: Txn[Unit] = pure(())

  def abort[A](e: Throwable): Txn[A] = Abort(e)

  private[stm] object Internals {

    case class Pure[A](a: A)                                extends Txn[A]
    case class Alloc[A](a: A)                               extends Txn[TVar[A]]
    case class Bind[A, B](stm: Txn[B], f: B => Txn[A])      extends Txn[A]
    case class Get[A](tvar: TVar[A])                        extends Txn[A]
    case class Modify[A](tvar: TVar[A], f: A => A)          extends Txn[Unit]
    case class OrElse[A](attempt: Txn[A], fallback: Txn[A]) extends Txn[A]
    case class Abort(error: Throwable)                      extends Txn[Nothing]
    case object Retry                                       extends Txn[Nothing]

    sealed trait TResult[+A]              extends Product with Serializable
    case class TSuccess[A](value: A)      extends TResult[A]
    case class TFailure(error: Throwable) extends TResult[Nothing]
    case object TRetry                    extends TResult[Nothing]

    type Cont   = Any => Txn[Any]
    type TVarId = Long
    type TxId   = Long

    case class TLog(private var map: Map[TVarId, TLogEntry]) {

      def values: Iterable[TLogEntry] = map.values

      def get(tvar: TVar[Any]): Any =
        if (map.contains(tvar.id))
          map(tvar.id).unsafeGet[Any]
        else {
          val current = tvar.value
          map = map + (tvar.id -> TLogEntry(tvar, current))
          current
        }

      def modify(tvar: TVar[Any], f: Any => Any): Unit =
        if (map.contains(tvar.id)) {
          val e       = map(tvar.id)
          val current = e.unsafeGet[Any]
          val entry   = e.unsafeSet[Any](f(current))
          map = map + (tvar.id -> entry)
        } else {
          val current = tvar.value
          map = map + (tvar.id -> TLogEntry(tvar, f(current)))
        }

      def isDirty: Boolean = values.exists(_.isDirty)

      def snapshot(): TLog = TLog(map)

      def delta(tlog: TLog): TLog =
        TLog(
          map.foldLeft(tlog.map) { (acc, p) =>
            val (id, e) = p
            if (acc.contains(id)) acc else acc + (id -> TLogEntry(e.tvar, e.tvar.value))
          }
        )

      def commit(implicit F: Concurrent[F]): F[Unit] = F.delay(values.foreach(_.commit()))

      def signal(implicit F: Concurrent[F]): F[Unit] =
        values.toList.traverse_(e =>
          for {
            signals <- e.tvar.retries.getAndSet(Nil)
            // _       <- signals.traverse_(s => F.delay(println("signalling")) >> s.complete(()))
            _       <- signals.traverse_(s => s.complete(()))
          } yield ()
        )

      def registerRetry(signal: Deferred[F, Unit])(implicit F: Monad[F]): F[Unit] =
        values.toList.traverse_(e => e.tvar.registerRetry(signal))
    }

    object TLog {
      def empty: TLog = TLog(Map.empty)
    }

    abstract class TLogEntry { self =>
      type Repr
      var current: Repr
      val initial: Repr
      val tvar: TVar[Repr]

      def unsafeGet[A]: A = current.asInstanceOf[A]

      def unsafeSet[A](a: A): TLogEntry = TLogEntry[Repr](tvar, a.asInstanceOf[Repr])

      def commit(): Unit = tvar.value = current

      def isDirty: Boolean = initial != tvar.value.asInstanceOf[Repr]

      def snapshot(): TLogEntry =
        new TLogEntry {
          override type Repr = self.Repr
          override var current: Repr    = self.current
          override val initial: Repr    = self.initial
          override val tvar: TVar[Repr] = self.tvar
        }

    }

    object TLogEntry {

      def apply[A](tvar0: TVar[A], current0: A): TLogEntry =
        new TLogEntry {
          override type Repr = A
          override var current: A    = current0
          override val initial: A    = tvar0.value.asInstanceOf[A]
          override val tvar: TVar[A] = tvar0
        }

    }

    def eval[A](idGen: Ref[F, Long], txn: Txn[A])(implicit F: Concurrent[F]): F[(TResult[A], TLog)] = {
      var conts: List[Cont]                             = Nil
      var fallbacks: List[(Txn[Any], TLog, List[Cont])] = Nil
      var log: TLog                                     = TLog.empty

      //Construction of a TVar requires allocating state but we want this to be tail-recursive
      //and non-effectful so we trampoline it with run
      @tailrec
      def go(
        nextId: TVarId,
        lock: Semaphore[F],
        ref: Ref[F, List[Deferred[F, Unit]]],
        txn: Txn[Any]
      ): Either[Txn[Any], TResult[Any]] =
        txn match {
          case Pure(a) =>
            if (conts.isEmpty)
              Right(TSuccess(a))
            else {
              val f = conts.head
              conts = conts.tail
              go(nextId, lock, ref, f(a))
            }
          case Alloc(a) => Left(Pure((new TVar(nextId, a, lock, ref))))
          case Bind(stm, f) =>
            conts = f :: conts
            go(nextId, lock, ref, stm)
          case Get(tvar)       => go(nextId, lock, ref, Pure(log.get(tvar)))
          case Modify(tvar, f) => go(nextId, lock, ref, Pure(log.modify(tvar.asInstanceOf[TVar[Any]], f)))
          case OrElse(attempt, fallback) =>
            fallbacks = (fallback, log.snapshot(), conts) :: fallbacks
            go(nextId, lock, ref, attempt)
          case Abort(error) =>
            Right(TFailure(error))
          case Retry =>
            if (fallbacks.isEmpty) Right(TRetry)
            else {
              val (fb, lg, cts) = fallbacks.head
              log = log.delta(lg)
              conts = cts
              fallbacks = fallbacks.tail
              go(nextId, lock, ref, fb)
            }
        }

      def run(txn: Txn[Any]): F[TResult[Any]] =
        for {
          id   <- idGen.updateAndGet(_ + 1)
          lock <- Semaphore[F](1)
          ref  <- Ref.of[F, List[Deferred[F, Unit]]](Nil)
          //Trampoline so we can generate a new id/lock/ref to supply
          //if we need to contruct a new tvar
          res <- go(id, lock, ref, txn) match {
            case Left(t)  => run(t)
            case Right(v) => F.pure(v)
          }
        } yield res

      //Safe by construction
      run(txn.asInstanceOf[Txn[Any]]).map(res => res.asInstanceOf[TResult[A]] -> log)
    }

  }

}

object STM {
  def apply[F[_]]()(implicit F: Concurrent[F]): F[STM[F]] =
    for {
      idGen  <- Ref.of[F, Long](0)
      global <- Semaphore[F](1) //TODO remove this and just lock each tvar
    } yield new STM[F] {
      import Internals._

      def commit[A](txn: Txn[A]): F[A] =
        for {
          signal <- Deferred[F, Unit]
          p      <- eval(idGen, txn)
          (res, log) = p
          // _ <- F.delay(println(s"res: $res log: $log"))
          r <- res match {
            //Double-checked dirtyness
            case TSuccess(a) =>
              if (log.isDirty) commit(txn)
              else
                for {
                  committed <- global.withPermit(
                    if (log.isDirty) F.pure(false)
                    else
                      // F.delay(println("committing")) >> log.commit.as(true)
                      log.commit.as(true)
                  )
                  r <- if (committed) log.signal >> F.pure(a) else commit(txn)
                } yield r
            case TFailure(e) => if (log.isDirty) commit(txn) else F.raiseError(e)
            //TODO make retry blocking safely cancellable
            case TRetry =>
              if (log.isDirty)
                //TODO we could probably split commit so we don't reallocate a signal every time
                commit(txn)
              //TODO remove signal from tvars when we wake
              //TODO we need a lock here?
              // else log.registerRetry(signal) >> signal.get >> F.delay(println("retrying")) >> commit(txn)
              else log.registerRetry(signal) >> signal.get >> commit(txn)
          }
        } yield r
    }

}
