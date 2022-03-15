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

import cats.{Contravariant, Functor, Invariant}
import cats.effect.IO

class TDeferredSpec extends BaseSpec {

  test("complete unblocks getters") {
    val stm = stmRuntime()
    import stm._
    for {
      d  <- stm.commit(TDeferred[Int])
      v  <- stm.commit(TVar.of(0))
      v1 <- stm.commit(TVar.of(0))
      v2 <- stm.commit(TVar.of(0))
      f1 <- stm.commit(d.get.flatMap(v1.set)).start
      f2 <- stm.commit(d.get.flatMap(v2.set)).start
      _ <- assertIO(
        stm.commit(d.complete(42).flatTap(_ => v.modify(_ + 1))),
        true
      )
      _ <- assertIO(stm.commit(v.get), 1)
      _ <- f1.joinWithNever
      _ <- assertIO(stm.commit(v1.get), 42)
      _ <- f2.joinWithNever
      _ <- assertIO(stm.commit(v2.get), 42)
    } yield ()
  }

  test("can only be completed once") {
    val stm = stmRuntime()
    import stm._
    for {
      d   <- stm.commit(TDeferred[Int])
      f   <- stm.commit(d.get).start
      c12 <- IO.both(stm.commit(d.complete(42)), stm.commit(d.complete(33)))
      (c1, c2) = c12
      _ <- IO(assert(c1 ^ c2))
      _ <- assertIO(f.joinWithNever, if (c1) 42 else 33)
    } yield ()
  }

  test("imap") {
    val stm = stmRuntime()
    import stm._
    for {
      d <- stm.commit(TDeferred[Int])
      dd = d.imap[String](_.toString)(_.toInt)
      f  <- stm.commit(d.get).start
      ff <- stm.commit(dd.get).start
      _  <- assertIO(stm.commit(dd.complete("42")), true)
      _  <- assertIO(f.joinWithNever, 42)
      _  <- assertIO(ff.joinWithNever, "42")
    } yield ()
  }

  test("map/contramap") {
    val stm = stmRuntime()
    import stm._
    for {
      d <- stm.commit(TDeferred[Int])
      dSource = d.map[String](_.toString)
      dSink   = d.contramap[String](_.toInt)
      f <- stm.commit(dSource.get).start
      _ <- assertIO(stm.commit(dSink.complete("42")), true)
      _ <- assertIO(f.joinWithNever, "42")
    } yield ()
  }

  test("instances") {
    val stm = stmRuntime()
    import stm._
    Invariant[TDeferred]
    Contravariant[TDeferredSink]
    Functor[TDeferredSource]
  }
}
