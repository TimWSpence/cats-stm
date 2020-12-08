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

import cats.effect.Async

/**
  * Convenience definition of a semaphore in the `STM` monad.
  *
  * Analogous to `cats.effect.concurrent.Semaphore`.
  */
trait TSemaphoreLike[F[_]] extends STMLike[F] {
  final class TSemaphore private[stm] (private val tvar: TVar[Long]) {

    /**
      * Get the number of permits currently available.
      */
    def available: Txn[Long] = tvar.get

    /**
      * Acquire a permit. Retries if no permits are
      * available.
      */
    def acquire: Txn[Unit] =
      tvar.get.flatMap {
        case 0 => retry
        case _ => tvar.modify(_ - 1)
      }

    /**
      * Release a currently held permit.
      */
    def release: Txn[Unit] = tvar.modify(_ + 1)
  }

  object TSemaphore {

    /**
      * Create a new `TSem` with `permits` available permits.
      */
    def make(permits: Long)(implicit F: Async[F]): Txn[TSemaphore] = TVar.of(permits).map(tvar => new TSemaphore(tvar))

  }

}
