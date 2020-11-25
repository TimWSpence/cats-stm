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

// import java.util.concurrent.atomic.AtomicReference

// import io.github.timwspence.cats.stm.STM.internal._

// /**
//   * Transactional variable - a mutable memory location
//   * that can be read or written to via `STM` actions.
//   *
//   * Analagous to `cats.effect.concurrent.Ref`.
//   */
// final class TVar[A] private[stm] (
//   private[stm] val id: Long,
//   @volatile private[stm] var value: A,
//   private[stm] val pending: AtomicReference[Map[TxId, RetryFiber]]
// ) {

//   /**
//     * Get the current value as an
//     * `STM` action.
//     */
//   def get: STM[A] = Get(this)

//   /**
//     * Set the current value as an
//     * `STM` action.
//     */
//   def set(a: A): STM[Unit] = modify(_ => a)

//   /**
//     * Modify the current value as an
//     * `STM` action.
//     */
//   def modify(f: A => A): STM[Unit] = Modify(this, f)

//   private[stm] def registerRetry(txId: TxId, fiber: RetryFiber): Unit = {
//     pending.updateAndGet(m => m + (txId -> fiber))
//     ()
//   }

//   private[stm] def unregisterRetry(txId: TxId): Unit = {
//     pending.updateAndGet(m => m - txId)
//     ()
//   }

//   private[stm] def unregisterAll(): Map[TxId, RetryFiber] = pending.getAndSet(Map.empty)

// }

// object TVar {

//   def of[A](value: A): STM[TVar[A]] = Alloc(value)

// }
