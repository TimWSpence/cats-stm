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

import cats.effect.IO
import munit.CatsEffectSuite

class TLogSpec extends CatsEffectSuite {

  val stm = STM.runtime[IO].unsafeRunSync()
  import stm._
  import stm.Internals._

  val inc: Int => Int = _ + 1

  test("getF when not present") {
    for {
      tvar <- stm.commit(TVar.of[Any](1))
      tlog = TLog.empty
      v <- tlog.getF(tvar)
      res <- IO {
        assertEquals(v, 1)
      }
    } yield res
  }

  test("get when present") {
    for {
      tvar <- stm.commit(TVar.of[Any](1))
      tlog = TLog.empty
      _ <- tlog.getF(tvar)
      res <- IO {
        tlog.modify(tvar, inc.asInstanceOf[Any => Any])
        assertEquals(tlog.get(tvar), 2)
      }
    } yield res
  }

  test("isDirty when empty") {
    val tlog = TLog.empty
    for {
      v   <- tlog.isDirty
      res <- IO(assert(!v))
    } yield res
  }

  test("isDirty when non-empty") {
    for {
      tvar <- stm.commit(TVar.of[Any](1))
      tlog = TLog.empty
      _   <- tlog.getF(tvar)
      v   <- tlog.isDirty
      res <- IO(assert(!v))
    } yield res
  }

  test("isDirty when non-empty and dirty") {
    for {
      tvar <- stm.commit(TVar.of[Any](1))
      tlog = TLog.empty
      _   <- tlog.getF(tvar)
      _   <- stm.commit(tvar.set(2))
      v   <- tlog.isDirty
      res <- IO(assert(v))
    } yield res
  }

  test("commit") {
    for {
      tvar <- stm.commit(TVar.of[Any](1))
      tlog = TLog.empty
      _ <- tlog.modifyF(tvar, inc.asInstanceOf[Any => Any])
      _ <- tlog.commit
      v <- stm.commit(tvar.get)
      res <- IO {
        assertEquals(v, 2)
      }
    } yield res
  }

  test("snapshot") {
    for {
      tvar  <- stm.commit(TVar.of[Any](1))
      tvar2 <- stm.commit(TVar.of[Any](2))
      tlog = TLog.empty
      _ <- tlog.modifyF(tvar, inc.asInstanceOf[Any => Any])
      tlog2 = tlog.snapshot()
      v <- tlog2.getF(tvar2)
      res <- IO {
        assertEquals(tlog2.get(tvar), 2)
        assertEquals(v, 2)

      }
    } yield res
  }

  test("delta") {
    for {
      tvar  <- stm.commit(TVar.of[Any](1))
      tvar2 <- stm.commit(TVar.of[Any](2))
      tlog = TLog.empty
      _ <- tlog.modifyF(tvar, inc.asInstanceOf[Any => Any])
      _ <- tlog.modifyF(tvar2, inc.asInstanceOf[Any => Any])
      tlog2 = tlog.snapshot()
      _ <- tlog2.modifyF(tvar2, inc.asInstanceOf[Any => Any])
      d = tlog2.delta(tlog)
      res <- IO {
        assertEquals(d.get(tvar), 2)
        assertEquals(d.get(tvar2), 3)
      }
    } yield res
  }

}
