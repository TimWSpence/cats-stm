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

import scala.concurrent.duration._

import cats.implicits._
import cats.effect.IO

class TVarSpec extends BaseSpec {

  stmTest("Get returns current value") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tvar  <- TVar.of("hello")
        value <- tvar.get
      } yield value)
      res <- IO(assertEquals(v, "hello"))
    } yield res
  }

  stmTest("Set changes current value") { stm =>
    import stm._
    for {
      v <- stm.commit(
        for {
          tvar  <- TVar.of("hello")
          _     <- tvar.set("world")
          value <- tvar.get
        } yield value
      )
      res <- IO(assertEquals(v, "world"))
    } yield res
  }

  stmTest("Modify changes current value") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tvar  <- TVar.of("hello")
        _     <- tvar.modify(_.toUpperCase)
        value <- tvar.get
      } yield value)
      res <- IO(assertEquals(v, "HELLO"))
    } yield res
  }

  stmTest("Transaction is registered for retry") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0))
      fiber <-
        stm
          .commit((for {
            current <- tvar.get
            _       <- stm.check(current > 0)
          } yield current))
          .start
      _   <- IO.sleep(1.second)
      _   <- stm.commit(tvar.set(2))
      v   <- fiber.joinWithNever
      res <- IO(assertEquals(v, 2))
    } yield res
  }

  stmTest("TVar.of is referentially transparent") { stm =>
    import stm._
    val t: Txn[TVar[Int]] = TVar.of(0)

    for {
      t1 <- stm.commit(t)
      t2 <- stm.commit(t)
      _ <- stm.commit((for {
        _ <- t1.modify(_ + 1)
        _ <- t2.modify(_ + 2)
      } yield ()))
      vs <- stm.commit((t1.get, t2.get).tupled)
      res <- IO {
        assertEquals(vs._1, 1)
        assertEquals(vs._2, 2)
      }
    } yield res
  }
}
