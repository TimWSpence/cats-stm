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

class TMVarSpec extends BaseSpec {

  stmTest("Read returns current value when not empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.of("hello")
        value <- tmvar.read
      } yield value)
      res <- IO(assertEquals(v, "hello"))
    } yield res
  }

  stmTest("Read does not modify value when not empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.of("hello")
        _     <- tmvar.read
        value <- tmvar.read
      } yield value)
      res <- IO(assertEquals(v, "hello"))
    } yield res
  }

  stmTest("Take returns current value when not empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.of("hello")
        value <- tmvar.take
      } yield value)
      res <- IO(assertEquals(v, "hello"))
    } yield res
  }

  stmTest("Take empties tmvar when not empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.of("hello")
        _     <- tmvar.take
        empty <- tmvar.isEmpty
      } yield empty)
      res <- IO(assert(v))
    } yield res
  }

  stmTest("Put stores a value when empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.empty[String]
        _     <- tmvar.put("hello")
        value <- tmvar.take
      } yield value)
      res <- IO(assertEquals(v, "hello"))
    } yield res
  }

  stmTest("TryPut returns true when empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar  <- TMVar.empty[String]
        result <- tmvar.tryPut("hello")
      } yield result)
      res <- IO(assert(v))
    } yield res
  }

  stmTest("TryPut returns false when not empty") { stm =>
    import stm._
    for {
      v <- stm.commit(
        for {
          tmvar  <- TMVar.of("world")
          result <- tmvar.tryPut("hello")
        } yield result
      )

      res <- IO(assert(!v))
    } yield res
  }

  stmTest("IsEmpty is false when not empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.of("world")
        empty <- tmvar.isEmpty
      } yield empty)
      res <- IO(assert(!v))
    } yield res
  }

  stmTest("IsEmpty is true when empty") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tmvar <- TMVar.empty[String]
        empty <- tmvar.isEmpty
      } yield empty)
      res <- IO(assert(v))
    } yield res
  }
}
