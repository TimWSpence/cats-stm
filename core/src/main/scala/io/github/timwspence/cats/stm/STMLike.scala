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

import cats.effect.std.Semaphore
import cats.effect.{Async, Concurrent, Deferred, Ref, Resource}
import cats.implicits._
import cats.data.EitherT
import cats.{MonadError, Monoid, MonoidK, StackSafeMonad}
import scala.reflect.ClassTag

trait STMLike[F[_]] {
  import Internals._

  def pure[A](a: A): Txn[A] = Txn.pure(a)

  def commit[A](txn: Txn[A]): F[A]

  def check(cond: => Boolean): Txn[Unit] = if (cond) unit else retry

  def retry[A]: Txn[A] = Retry

  val unit: Txn[Unit] = pure(())

  def abort[A](e: Throwable): Txn[A] = Txn.abort(e)

  def raiseError[A](e: Throwable): Txn[A] = abort(e)

  class TVar[A] private[stm] (
    private[stm] val id: TVarId,
    private[stm] val value: Ref[F, A],
    private[stm] val lock: Semaphore[F],
    private[stm] val retries: Ref[F, List[Deferred[F, Unit]]]
  ) {
    def get: Txn[A] = Get(this)

    def modify(f: A => A): Txn[Unit] = Modify(this, f)

    def set(a: A): Txn[Unit] = modify(_ => a)

    private[stm] def registerRetry(signal: Deferred[F, Unit]): F[Unit] =
      retries.update(signal :: _)

  }

  object TVar {
    def of[A](a: A)(implicit F: Concurrent[F]): Txn[TVar[A]] = Alloc(F.ref(a))
  }

  sealed abstract class Txn[+A] {

    private[stm] val tag: T

    /*
     * ALias for [[productL]]
     */
    final def <*[B](that: Txn[B]): Txn[A] =
      productL(that)

    /*
     * Alias for [[productR]]
     */
    final def *>[B](that: Txn[B]): Txn[B] =
      productR(that)

    final def >>[B](that: => Txn[B]): Txn[B] =
      flatMap(_ => that)

    /**
      * Transform certain errors using `pf` and rethrow them.
      * Non matching errors and successful values are not affected by this function.
      */
    final def adaptError(pf: PartialFunction[Throwable, Throwable]): Txn[A] =
      recoverWith(pf.andThen(raiseError[A] _))

    /*
     * Replaces the `A` value in `F[A]` with the supplied value.
     */
    final def as[B](b: B): Txn[B] =
      map(_ => b)

    /**
      * Handle errors by turning them into Either values.
      *
      * If there is no error, then an `scala.util.Right` value will be returned instead.
      */
    final def attempt: Txn[Either[Throwable, A]] =
      map(Right(_): Either[Throwable, A]).handleErrorWith(e => pure(Left(e)))

    /**
      * Similar to [[attempt]], but it only handles errors of type `EE`.
      */
    def attemptNarrow[EE <: Throwable](implicit tag: ClassTag[EE]): Txn[Either[EE, A]] =
      map(Right[EE, A](_): Either[EE, A]).recover { case e: EE => Left[EE, A](e) }

    /**
      * Similar to [[attempt]], but wraps the result in a EitherT for
      * convenience.
      */
    final def attemptT[B >: A]: EitherT[Txn, Throwable, B] = EitherT(attempt)

    /**
      * Reifies the value or error of the source and performs an effect on the result,
      * then recovers the original value or error back into `F`.
      *
      * Note that if the effect returned by `f` fails, the resulting effect will fail too.
      */
    final def attemptTap[B](f: Either[Throwable, A] => Txn[B]): Txn[A] =
      attempt.flatTap(f).rethrow

    /**
      * Turns a successful value into an error if it does not satisfy a given predicate.
      */
    final def ensure(error: => Throwable)(predicate: A => Boolean): Txn[A] =
      flatMap(a => if (predicate(a)) pure(a) else raiseError(error))

    /**
      * Turns a successful value into an error specified by the `error` function if it does not satisfy a given predicate.
      */
    final def ensureOr(error: A => Throwable)(predicate: A => Boolean): Txn[A] =
      flatMap(a => if (predicate(a)) pure(a) else raiseError(error(a)))

    /**
      * Monadic bind on `STM`.
      */
    final def flatMap[B](f: A => Txn[B]): Txn[B] = Bind(this, f)

    final def flatTap[B](f: A => Txn[B]): Txn[A] =
      flatMap(a => (f(a).as(a)))

    /**
      * "flatten" a nested `F` of `F` structure into a single-layer `F` structure.
      *
      * This is also commonly called `join`.
      */
    final def flatten[B](implicit ev: A <:< Txn[B]): Txn[B] = flatMap(ev)

    /**
      * Alias for [[map]]
      */
    final def fmap[B](f: A => B): Txn[B] = map(f)

    /**
      * Tuple the values in fa with the result of applying a function
      * with the value
      */
    final def fproduct[B](f: A => B): Txn[(A, B)] = map(a => a -> f(a))

    /**
      *  Pair the result of function application with `A`.
      */
    final def fproductLeft[B](f: A => B): Txn[(B, A)] = map(a => f(a) -> a)

    /**
      * Handle any error, by mapping it to an `A` value.
      *
      * @see [[handleErrorWith]] to map to an `F[A]` value instead of simply an
      * `A` value.
      *
      * @see [[recover]] to only recover from certain errors.
      */
    final def handleError[B >: A](f: Throwable => B): Txn[B] = handleErrorWith(f.andThen(pure))

    /**
      * Handle any error, potentially recovering from it, by mapping it to an
      * `F[A]` value.
      *
      * @see [[handleError]] to handle any error by simply mapping it to an `A`
      * value instead of an `F[A]`.
      *
      * @see [[recoverWith]] to recover from only certain errors.
      */
    final def handleErrorWith[B >: A](f: Throwable => Txn[B]): Txn[B] = HandleError(this, f)

    /**
      * Functor map on `STM`.
      */
    final def map[B](f: A => B): Txn[B] = Bind(this, f.andThen(Pure(_)))

    final def map2[B, Z](fb: Txn[B])(f: (A, B) => Z): Txn[Z] =
      flatMap(a => fb.map(b => f(a, b)))

    /**
      * Execute a callback on certain errors, then rethrow them.
      * Any non matching error is rethrown as well.
      */
    final def onError(pf: PartialFunction[Throwable, Txn[Unit]]): Txn[A] =
      handleErrorWith(e => (pf.andThen(_.map2(raiseError[A](e))((_, b) => b))).applyOrElse(e, raiseError))

    /**
      * Try an alternative `STM` action if this one retries.
      */
    final def orElse[B >: A](other: Txn[B]): Txn[B] = OrElse(this, other)

    final def product[B](that: Txn[B]): Txn[(A, B)] =
      flatMap(a => that.map(b => (a, b)))

    final def productL[B](that: Txn[B]): Txn[A] =
      flatMap(a => that.as(a))

    final def productR[B](that: Txn[B]): Txn[B] =
      flatMap(_ => that)

    /**
      * Recover from certain errors by mapping them to an `A` value.
      *
      * @see [[handleError]] to handle any/all errors.
      *
      * @see [[recoverWith]] to recover from certain errors by mapping them to
      * `F[A]` values.
      */
    final def recover[B >: A](pf: PartialFunction[Throwable, B]): Txn[B] =
      handleErrorWith(e => (pf.andThen(pure(_))).applyOrElse(e, raiseError[A](_)))

    /**
      * Recover from certain errors by mapping them to an `F[A]` value.
      *
      * @see [[handleErrorWith]] to handle any/all errors.
      *
      * @see [[recover]] to recover from certain errors by mapping them to `A`
      * values.
      */
    final def recoverWith[B >: A](pf: PartialFunction[Throwable, Txn[B]]): Txn[B] =
      handleErrorWith(e => pf.applyOrElse(e, raiseError))

    /**
      * Returns a new value that transforms the result of the source,
      * given the `recover` or `map` functions, which get executed depending
      * on whether the result is successful or if it ends in error.
      */
    final def redeem[B](recover: Throwable => B, map: A => B): Txn[B] =
      attempt.map(_.fold(recover, map))

    /**
      * Returns a new value that transforms the result of the source,
      * given the `recover` or `bind` functions, which get executed depending
      * on whether the result is successful or if it ends in error.
      */
    final def redeemWith[B](recover: Throwable => Txn[B], bind: A => Txn[B]): Txn[B] =
      attempt.flatMap(_.fold(recover, bind))

    /**
      * Inverse of `attempt`
      */
    final def rethrow[B](implicit ev: A <:< Either[Throwable, B]): Txn[B] =
      flatMap(_.fold(raiseError, pure))

    /**
      * Tuples the `A` value in `Txn[A]` with the supplied `B` value, with the `B` value on the left.
      */
    final def tupleLeft[B](b: B): Txn[(B, A)] = map(a => (b, a))

    /**
      * Tuples the `A` value in `Txn[A]` with the supplied `B` value, with the `B` value on the right.
      */
    final def tupleRight[B](b: B): Txn[(A, B)] = map(a => (a, b))

    /*
     * Empty the txn of the values, preserving the structure
     *
     */
    final def void: Txn[Unit] =
      map(_ => ())

    /**
      * Lifts natural subtyping covariance of covariant Functors.
      */
    final def widen[B >: A]: Txn[B] = this.asInstanceOf[Txn[B]]
  }

  object Txn {
    def pure[A](a: A): Txn[A] = Pure(a)

    def retry[A]: Txn[A] = Retry

    def abort[A](e: Throwable): Txn[A] = Abort(e)

    implicit val txnMonad: StackSafeMonad[Txn] with MonadError[Txn, Throwable] with MonoidK[Txn] =
      new StackSafeMonad[Txn] with MonadError[Txn, Throwable] with MonoidK[Txn] {

        override def adaptError[A](fa: Txn[A])(pf: PartialFunction[Throwable, Throwable]): Txn[A] =
          fa.adaptError(pf)

        override def as[A, B](fa: Txn[A], b: B): Txn[B] = fa.as(b)

        override def attempt[A](fa: Txn[A]): Txn[Either[Throwable, A]] = fa.attempt

        override def attemptNarrow[EE <: Throwable, A](
          fa: Txn[A]
        )(implicit tag: ClassTag[EE], ev: EE <:< Throwable): Txn[Either[EE, A]] =
          fa.attemptNarrow[EE]

        override def attemptT[A](fa: Txn[A]): EitherT[Txn, Throwable, A] =
          fa.attemptT

        override def attemptTap[A, B](fa: Txn[A])(f: Either[Throwable, A] => Txn[B]): Txn[A] =
          fa.attemptTap(f)

        override def combineK[A](x: Txn[A], y: Txn[A]): Txn[A] = x.orElse(y)

        override def empty[A]: Txn[A] = Txn.retry

        override def ensure[A](fa: Txn[A])(error: => Throwable)(predicate: A => Boolean): Txn[A] =
          fa.ensure(error)(predicate)

        override def ensureOr[A](fa: Txn[A])(error: A => Throwable)(predicate: A => Boolean): Txn[A] =
          fa.ensureOr(error)(predicate)

        override def flatMap[A, B](fa: Txn[A])(f: A => Txn[B]): Txn[B] = fa.flatMap(f)

        override def flatTap[A, B](fa: Txn[A])(f: A => Txn[B]): Txn[A] =
          fa.flatTap(f)

        override def flatten[A](ffa: Txn[Txn[A]]): Txn[A] =
          ffa.flatten

        override def fproduct[A, B](fa: Txn[A])(f: A => B): Txn[(A, B)] = fa.fproduct(f)

        override def fproductLeft[A, B](fa: Txn[A])(f: A => B): Txn[(B, A)] = fa.fproductLeft(f)

        override def handleError[A](fa: Txn[A])(f: Throwable => A): Txn[A] =
          fa.handleError(f)

        override def handleErrorWith[A](fa: Txn[A])(f: Throwable => Txn[A]): Txn[A] = fa.handleErrorWith(f)

        override def map[A, B](fa: Txn[A])(f: A => B): Txn[B] =
          fa.map(f)

        override def map2[A, B, Z](fa: Txn[A], fb: Txn[B])(f: (A, B) => Z): Txn[Z] =
          fa.map2(fb)(f)

        override def onError[A](fa: Txn[A])(pf: PartialFunction[Throwable, Txn[Unit]]): Txn[A] =
          fa.onError(pf)

        override def pure[A](x: A): Txn[A] = Txn.pure(x)

        override def product[A, B](fa: Txn[A], fb: Txn[B]): Txn[(A, B)] =
          fa.product(fb)

        override def productL[A, B](fa: Txn[A])(fb: Txn[B]): Txn[A] =
          fa.productL(fb)

        override def productR[A, B](fa: Txn[A])(fb: Txn[B]): Txn[B] =
          fa.productR(fb)

        override def raiseError[A](e: Throwable): Txn[A] = Txn.abort(e)

        override def recover[A](fa: Txn[A])(pf: PartialFunction[Throwable, A]): Txn[A] =
          fa.recover(pf)

        override def recoverWith[A](fa: Txn[A])(pf: PartialFunction[Throwable, Txn[A]]): Txn[A] =
          fa.recoverWith(pf)

        override def redeem[A, B](fa: Txn[A])(recover: Throwable => B, f: A => B): Txn[B] =
          fa.redeem(recover, f)

        override def redeemWith[A, B](fa: Txn[A])(recover: Throwable => Txn[B], bind: A => Txn[B]): Txn[B] =
          fa.redeemWith(recover, bind)

        override def rethrow[A, EE <: Throwable](fa: Txn[Either[EE, A]]): Txn[A] =
          fa.rethrow

        override def tupleLeft[A, B](fa: Txn[A], b: B): Txn[(B, A)] =
          fa.tupleLeft(b)

        override def tupleRight[A, B](fa: Txn[A], b: B): Txn[(A, B)] =
          fa.tupleRight(b)

        override def void[A](fa: Txn[A]): Txn[Unit] = fa.void

        override def widen[A, B >: A](fa: Txn[A]): Txn[B] = fa.widen
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

  private[stm] object Internals {

    type T = Byte
    val PureT: T        = 0
    val AllocT: T       = 1
    val BindT: T        = 2
    val HandleErrorT: T = 3
    val GetT: T         = 4
    val ModifyT: T      = 5
    val OrElseT: T      = 6
    val AbortT: T       = 7
    val RetryT: T       = 8

    case class Pure[A](a: A) extends Txn[A] {
      private[stm] val tag: T = PureT
    }
    case class Alloc[A](v: F[Ref[F, A]]) extends Txn[TVar[A]] {
      private[stm] val tag: T = AllocT
    }
    case class Bind[A, B](txn: Txn[B], f: B => Txn[A]) extends Txn[A] {
      private[stm] val tag: T = BindT
    }
    case class HandleError[A](txn: Txn[A], recover: Throwable => Txn[A]) extends Txn[A] {
      private[stm] val tag: T = HandleErrorT
    }
    case class Get[A](tvar: TVar[A]) extends Txn[A] {
      private[stm] val tag: T = GetT
    }
    case class Modify[A](tvar: TVar[A], f: A => A) extends Txn[Unit] {
      private[stm] val tag: T = ModifyT
    }
    case class OrElse[A](txn: Txn[A], fallback: Txn[A]) extends Txn[A] {
      private[stm] val tag: T = OrElseT
    }
    case class Abort(error: Throwable) extends Txn[Nothing] {
      private[stm] val tag: T = AbortT
    }
    case object Retry extends Txn[Nothing] {
      private[stm] val tag: T = RetryT
    }

    sealed trait TResult[+A]              extends Product with Serializable
    case class TSuccess[A](value: A)      extends TResult[A]
    case class TFailure(error: Throwable) extends TResult[Nothing]
    case object TRetry                    extends TResult[Nothing]

    type TVarId = Long

    case class TLog(private var map: Map[TVarId, TLogEntry]) {

      def values: Iterable[TLogEntry] = map.values

      def contains(tvar: TVar[Any]): Boolean = map.contains(tvar.id)

      /*
       * Get the current value of tvar in the txn. Throws if
       * tvar is not present in the log already
       */
      def get(tvar: TVar[Any]): Any = map(tvar.id).get

      /*
       * Get the current value of tvar in the txn log
       *
       * Assumes the tvar is not already in the txn log so
       * fetches the current value directly from the tvar
       */
      def getF(tvar: TVar[Any])(implicit F: Async[F]): F[Any] =
        tvar.value.get.flatMap { v =>
          F.delay {
            val e = TLogEntry(v, v, tvar)
            map = map + (tvar.id -> e)
            v
          }
        }

      /*
       * Modify the current value of tvar in the txn log. Throws if
       * tvar is not present in the log already
       */
      def modify(tvar: TVar[Any], f: Any => Any): Unit = {
        val e       = map(tvar.id)
        val current = e.get
        val entry   = e.set(f(current))
        map = map + (tvar.id -> entry)
      }

      /*
       * Modify the current value of tvar in the txn log
       *
       * Assumes the tvar is not already in the txn log so
       * fetches the current value directly from the tvar
       */
      def modifyF(tvar: TVar[Any], f: Any => Any)(implicit F: Async[F]): F[Unit] =
        tvar.value.get.flatMap { v =>
          F.delay {
            val e = TLogEntry(v, f(v), tvar)
            map = map + (tvar.id -> e)
          }
        }

      def isDirty(implicit F: Concurrent[F]): F[Boolean] =
        values.foldLeft(F.pure(false))((acc, v) =>
          for {
            d  <- acc
            d1 <- v.isDirty
          } yield d || d1
        )

      def snapshot(): TLog = TLog(map)

      def delta(tlog: TLog): TLog =
        TLog(
          map.foldLeft(tlog.map) { (acc, p) =>
            val (id, e) = p
            if (acc.contains(id)) acc else acc + (id -> TLogEntry(e.initial, e.initial, e.tvar))
          }
        )

      def withLock[A](fa: F[A])(implicit F: Concurrent[F]): F[A] =
        values.toList
          .sortBy(_.tvar.id)
          .foldLeft(Resource.liftF(F.unit)) { (locks, e) =>
            locks >> e.tvar.lock.permit
          }
          .use(_ => fa)

      def commit(implicit F: Concurrent[F]): F[Unit] = F.uncancelable(_ => values.toList.traverse_(_.commit))

      def signal(implicit F: Concurrent[F]): F[Unit] =
        //TODO use chain to avoid reverse?
        F.uncancelable(_ =>
          values.toList.reverse.traverse_(e =>
            for {
              signals <- e.tvar.retries.getAndSet(Nil)
              _       <- signals.traverse_(s => s.complete(()))
            } yield ()
          )
        )

      def registerRetry(signal: Deferred[F, Unit])(implicit F: Concurrent[F]): F[Unit] =
        values.toList.traverse_(e => e.tvar.registerRetry(signal))
    }

    object TLog {
      def empty: TLog = TLog(Map.empty)
    }

    case class TLogEntry(initial: Any, current: Any, tvar: TVar[Any]) { self =>

      def get: Any = current

      def set(a: Any): TLogEntry = TLogEntry(initial, a, tvar)

      def commit: F[Unit] = tvar.value.set(current)

      def isDirty(implicit F: Concurrent[F]): F[Boolean] = tvar.value.get.map(_ != initial)

      def snapshot(): TLogEntry = TLogEntry(self.initial, self.current, self.tvar)

    }

    object TLogEntry {

      def applyF[A](tvar0: TVar[A], current0: A)(implicit F: Async[F]): F[TLogEntry] =
        tvar0.value.get.map { v =>
          TLogEntry(v, current0, tvar0.asInstanceOf[TVar[Any]])
        }

    }

    def eval[A](idGen: Ref[F, Long], txn: Txn[A])(implicit F: Async[F]): F[(TResult[A], TLog)] = {

      sealed trait Trampoline
      case class Done(result: TResult[Any]) extends Trampoline
      case class Eff(run: F[Txn[Any]])      extends Trampoline

      type Cont = Any => Txn[Any]
      type Tag  = Int
      val cont: Tag   = 0
      val handle: Tag = 1

      var conts: List[Cont]                                                    = Nil
      var tags: List[Tag]                                                      = Nil
      var fallbacks: List[(Txn[Any], TLog, List[Cont], List[Tag], List[TLog])] = Nil
      var errorFallbacks: List[TLog]                                           = Nil
      var log: TLog                                                            = TLog.empty

      //Construction of a TVar requires allocating state but we want this to be tail-recursive
      //and non-effectful so we trampoline it with run
      @tailrec
      def go(
        nextId: TVarId,
        lock: Semaphore[F],
        ref: Ref[F, List[Deferred[F, Unit]]],
        txn: Txn[Any]
      ): Trampoline =
        txn.tag match {
          case PureT =>
            val t = txn.asInstanceOf[Pure[Any]]
            while (!tags.isEmpty && !(tags.head == cont)) {
              tags = tags.tail
              conts = conts.tail
            }
            if (tags.isEmpty)
              Done(TSuccess(t.a))
            else {
              val f = conts.head
              conts = conts.tail
              tags = tags.tail
              go(nextId, lock, ref, f(t.a))
            }
          case AllocT =>
            val t = txn.asInstanceOf[Alloc[Any]]
            Eff(t.v.map(v => Pure((new TVar(nextId, v, lock, ref)))))
          case BindT =>
            val t = txn.asInstanceOf[Bind[Any, Any]]
            conts = t.f :: conts
            tags = cont :: tags
            go(nextId, lock, ref, t.txn)
          case HandleErrorT =>
            val t = txn.asInstanceOf[HandleError[Any]]
            conts = t.recover.asInstanceOf[Any => Txn[Any]] :: conts
            tags = handle :: tags
            errorFallbacks = log.snapshot() :: errorFallbacks
            go(nextId, lock, ref, t.txn)
          case GetT =>
            val t = txn.asInstanceOf[Get[Any]]
            if (log.contains(t.tvar))
              go(nextId, lock, ref, Pure(log.get(t.tvar)))
            else
              Eff(log.getF(t.tvar).map(Pure(_)))
          case ModifyT =>
            val t = txn.asInstanceOf[Modify[Any]]
            if (log.contains(t.tvar))
              go(nextId, lock, ref, Pure(log.modify(t.tvar, t.f)))
            else
              Eff(log.modifyF(t.tvar, t.f).map(Pure(_)))
          case OrElseT =>
            val t = txn.asInstanceOf[OrElse[Any]]
            fallbacks = (t.fallback, log.snapshot(), conts, tags, errorFallbacks) :: fallbacks
            go(nextId, lock, ref, t.txn)
          case AbortT =>
            val t = txn.asInstanceOf[Abort]
            while (!tags.isEmpty && !(tags.head == handle)) {
              tags = tags.tail
              conts = conts.tail
            }
            if (tags.isEmpty) Done(TFailure(t.error))
            else {
              val f = conts.head
              conts = conts.tail
              tags = tags.tail
              log = errorFallbacks.head
              errorFallbacks = errorFallbacks.tail
              go(nextId, lock, ref, f(t.error))
            }
          case RetryT =>
            if (fallbacks.isEmpty) Done(TRetry)
            else {
              val (fb, lg, cts, tgs, efbs) = fallbacks.head
              log = log.delta(lg)
              conts = cts
              tags = tgs
              fallbacks = fallbacks.tail
              errorFallbacks = efbs
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
            case Done(v)   => F.pure(v)
            case Eff(ftxn) => ftxn.flatMap(run(_))
          }
        } yield res

      //Safe by construction
      run(txn.asInstanceOf[Txn[Any]]).map(res => res.asInstanceOf[TResult[A]] -> log)
    }

  }

}
