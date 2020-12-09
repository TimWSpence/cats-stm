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

import scala.collection.immutable.Queue

import cats.effect.Async

/**
  * Convenience definition of a queue in the `STM` monad.
  */
trait TQueueLike[F[_]] extends STMLike[F] {
  final class TQueue[A] private[stm] (private val tvar: TVar[Queue[A]]) {

    /**
      * Enqueue a value.
      */
    def put(a: A): Txn[Unit] = tvar.modify(_.enqueue(a))

    /**
      * Dequeue the first element. Retries if currently empty.
      */
    def read: Txn[A] =
      tvar.get.flatMap {
        case q if q.isEmpty => retry
        case q =>
          val (head, tail) = q.dequeue
          tvar.set(tail) >> pure(head)
      }

    /**
      * Peek the first element. Retries if empty.
      */
    def peek: Txn[A] =
      tvar.get.flatMap {
        case q if q.isEmpty => retry
        case q              => pure(q.head)
      }

    /**
      * Attempt to dequeue the first element. Returns
      * `None` if empty, `Some(head)` otherwise.
      */
    def tryRead: Txn[Option[A]] =
      tvar.get.flatMap {
        case q if q.isEmpty => pure(None)
        case q =>
          val (head, tail) = q.dequeue
          tvar.set(tail) >> pure(Some(head))
      }

    /**
      * Attempt to peek the first element. Returns
      * `None` if empty, `Some(head)` otherwise.
      */
    def tryPeek: Txn[Option[A]] = tvar.get.map(_.headOption)

    /**
      * Check if currently empty.
      */
    def isEmpty: Txn[Boolean] = tryPeek.map(_.isEmpty)

  }

  object TQueue {

    /**
      * Create a new empty `TQueue`.
      */
    def empty[A](implicit F: Async[F]): Txn[TQueue[A]] = TVar.of(Queue.empty[A]).map(tvar => new TQueue[A](tvar))

  }

}
