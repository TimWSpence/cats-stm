package io.github.timwspence.cats.stm

import cats.syntax.flatMap._

/**
  * Convenience definition providing `MVar`-like behaviour
  * in the `STM` monad. That is, a `TMVar` is a mutable memory
  * location which is either empty or contains a value.
  *
  * Analogous to `cats.effect.concurrent.MVar`.
  */
class TMVar[A] private[stm] (private val tvar: TVar[Option[A]]) extends AnyVal {

  /**
    * Store a value. Retries if the `TMVar` already
    * contains a value.
    */
  def put(a: A): STM[Unit] = tvar.get.flatMap {
    case Some(_) => STM.retry
    case None    => tvar.set(Some(a))
  }

  /**
    * Read the current value. Retries if empty.
    */
  def read: STM[A] = tvar.get.flatMap {
    case Some(value) => STM.pure(value)
    case None        => STM.retry
  }

  /**
    * Read the current value and empty it at the same
    * time. Retries if empty.
    */
  def take: STM[A] = tvar.get.flatMap {
    case Some(value) => tvar.set(None) >> STM.pure(value)
    case None        => STM.retry
  }

  /**
    * Try to store a value. Returns `false` if put failed,
    * `true` otherwise.
    */
  def tryPut(a: A): STM[Boolean] = tvar.get.flatMap {
    case Some(_) => STM.pure(false)
    case None    => tvar.set(Some(a)) >> STM.pure(true)
  }

  /**
    * Try to read the current value. Returns `None` if
    * empty, `Some(current)` otherwise.
    */
  def tryRead: STM[Option[A]] = tvar.get

  /**
    * Try to take the current value. Returns `None` if
    * empty, `Some(current)` otherwise.
    */
  def tryTake: STM[Option[A]] = tvar.get.flatMap {
    case v @ Some(_) => tvar.set(None) >> STM.pure(v)
    case None        => STM.pure(None)
  }

  /**
    * Check if currently empty.
    */
  def isEmpty: STM[Boolean] = tryRead.map(_.isEmpty)

}

object TMVar {

  /**
    * Create a new `TMVar`, initialized with a value.
    */
  def of[A](value: A): STM[TMVar[A]] = make(Some(value))

  /**
    * Create a new empty `TMVar`.
    */
  def empty[A]: STM[TMVar[A]] = make(None)

  private def make[A](value: Option[A]): STM[TMVar[A]] = TVar.of(value).map(tvar => new TMVar[A](tvar))

}
