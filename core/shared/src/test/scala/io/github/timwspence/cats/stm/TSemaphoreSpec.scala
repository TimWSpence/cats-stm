/*
 * Copyright 2017 TimWSpence
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

import cats.effect.IO

class TSemaphoreSpec extends BaseSpec {

  stmTest("Acquire decrements the number of permits") { stm =>
    import stm._
    for {
      v <- stm.commit(
        for {
          tsem  <- TSemaphore.make(1)
          _     <- tsem.acquire
          value <- tsem.available
        } yield value
      )
      res <- IO(assertEquals(v, 0L))
    } yield res
  }

  stmTest("Release increments the number of permits") { stm =>
    import stm._
    for {
      v <- stm.commit(
        for {
          tsem  <- TSemaphore.make(0)
          _     <- tsem.release
          value <- tsem.available
        } yield value
      )
      res <- IO(assertEquals(v, 1L))
    } yield res
  }

}
