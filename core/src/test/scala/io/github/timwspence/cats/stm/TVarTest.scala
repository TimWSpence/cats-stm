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

import scala.concurrent.duration._

import cats.effect.IO
import munit.CatsEffectSuite

class TVarTest extends CatsEffectSuite {

  val stm = STM[IO]().unsafeRunSync()
  import stm._

  test("Get returns current value") {
    val prog: Txn[String] = for {
      tvar  <- TVar.of("hello")
      value <- tvar.get
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "hello")
  }

  test("Set changes current value") {
    val prog: Txn[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.set("world")
      value <- tvar.get
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "world")
  }

  test("Modify changes current value") {
    val prog: Txn[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "HELLO")
  }

  test("Transaction is registered for retry") {
    val tvar = stm.commit(TVar.of(0)).unsafeRunSync()

    val prog: IO[Int] = for {
      fiber <-
        stm
          .commit((for {
            current <- tvar.get
            _       <- stm.check(current > 0)
          } yield current))
          .start
      _   <- IO.sleep(1 second)
      _   <- stm.commit(tvar.set(2))
      res <- fiber.joinAndEmbedNever
    } yield res

    prog.map { res =>
      assertEquals(res, 2)
    }

  }

  test("TVar.of is referentially transparent") {
    val t: Txn[TVar[Int]] = TVar.of(0)

    val prog = for {
      t1 <- stm.commit(t)
      t2 <- stm.commit(t)
      _ <- stm.commit((for {
        _ <- t1.modify(_ + 1)
        _ <- t2.modify(_ + 2)
      } yield ()))
      v1 <- stm.commit(t1.get)
      v2 <- stm.commit(t2.get)
    } yield (v1 -> v2)

    prog.map { res =>
      assertEquals(res._1, 1)
      assertEquals(res._2, 2)
    }
  }
}
