package io.github.timwspence.cats.stm

import scala.collection.immutable.Queue

import cats.syntax.flatMap._

/**
  * Convenience definition of a queue in the `STM` monad.
  */
final class TQueue[A] private[stm] (private val tvar: TVar[Queue[A]]) {

  /**
    * Enqueue a value.
    */
  def put(a: A): STM[Unit] = tvar.modify(_.enqueue(a))

  /**
    * Dequeue the first element. Retries if currently empty.
    */
  def read: STM[A] =
    tvar.get.flatMap {
      case q if q.isEmpty => STM.retry
      case q =>
        val (head, tail) = q.dequeue
        tvar.set(tail) >> STM.pure(head)
    }

  /**
    * Peek the first element. Retries if empty.
    */
  def peek: STM[A] =
    tvar.get.flatMap {
      case q if q.isEmpty => STM.retry
      case q              => STM.pure(q.head)
    }

  /**
    * Attempt to dequeue the first element. Returns
    * `None` if empty, `Some(head)` otherwise.
    */
  def tryRead: STM[Option[A]] =
    tvar.get.flatMap {
      case q if q.isEmpty => STM.pure(None)
      case q =>
        val (head, tail) = q.dequeue
        tvar.set(tail) >> STM.pure(Some(head))
    }

  /**
    * Attempt to peek the first element. Returns
    * `None` if empty, `Some(head)` otherwise.
    */
  def tryPeek: STM[Option[A]] = tvar.get.map(_.headOption)

  /**
    * Check if currently empty.
    */
  def isEmpty: STM[Boolean] = tryPeek.map(_.isEmpty)

}

object TQueue {

  /**
    * Create a new empty `TQueue`.
    */
  def empty[A]: STM[TQueue[A]] = TVar.of(Queue.empty[A]).map(tvar => new TQueue[A](tvar))

}
