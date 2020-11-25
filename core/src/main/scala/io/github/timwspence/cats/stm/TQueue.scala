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

// /*
//  * Copyright 2020 TimWSpence
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package io.github.timwspence.cats.stm

// import scala.collection.immutable.Queue

// import cats.syntax.flatMap._

// /**
//   * Convenience definition of a queue in the `STM` monad.
//   */
// final class TQueue[A] private[stm] (private val tvar: TVar[Queue[A]]) {

//   /**
//     * Enqueue a value.
//     */
//   def put(a: A): STM[Unit] = tvar.modify(_.enqueue(a))

//   /**
//     * Dequeue the first element. Retries if currently empty.
//     */
//   def read: STM[A] =
//     tvar.get.flatMap {
//       case q if q.isEmpty => STM.retry
//       case q =>
//         val (head, tail) = q.dequeue
//         tvar.set(tail) >> STM.pure(head)
//     }

//   /**
//     * Peek the first element. Retries if empty.
//     */
//   def peek: STM[A] =
//     tvar.get.flatMap {
//       case q if q.isEmpty => STM.retry
//       case q              => STM.pure(q.head)
//     }

//   /**
//     * Attempt to dequeue the first element. Returns
//     * `None` if empty, `Some(head)` otherwise.
//     */
//   def tryRead: STM[Option[A]] =
//     tvar.get.flatMap {
//       case q if q.isEmpty => STM.pure(None)
//       case q =>
//         val (head, tail) = q.dequeue
//         tvar.set(tail) >> STM.pure(Some(head))
//     }

//   /**
//     * Attempt to peek the first element. Returns
//     * `None` if empty, `Some(head)` otherwise.
//     */
//   def tryPeek: STM[Option[A]] = tvar.get.map(_.headOption)

//   /**
//     * Check if currently empty.
//     */
//   def isEmpty: STM[Boolean] = tryPeek.map(_.isEmpty)

// }

// object TQueue {

//   /**
//     * Create a new empty `TQueue`.
//     */
//   def empty[A]: STM[TQueue[A]] = TVar.of(Queue.empty[A]).map(tvar => new TQueue[A](tvar))

// }
