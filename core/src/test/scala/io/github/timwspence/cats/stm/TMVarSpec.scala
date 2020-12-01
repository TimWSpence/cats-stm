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

class TMVarTest extends CatsEffectSuite {

  val stm = STM[IO].unsafeRunSync()
  import stm._

  test("Read returns current value when not empty") {
    val prog: Txn[String] = for {
      tmvar <- TMVar.of("hello")
      value <- tmvar.read
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "hello")
  }

  test("Read does not modify value when not empty") {
    val prog: Txn[String] = for {
      tmvar <- TMVar.of("hello")
      _     <- tmvar.read
      value <- tmvar.read
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "hello")
  }

  test("Take returns current value when not empty") {
    val prog: Txn[String] = for {
      tmvar <- TMVar.of("hello")
      value <- tmvar.take
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "hello")
  }

  test("Take empties tmvar when not empty") {
    val prog: Txn[Boolean] = for {
      tmvar <- TMVar.of("hello")
      _     <- tmvar.take
      empty <- tmvar.isEmpty
    } yield empty

    for (value <- stm.commit(prog)) yield assert(value)
  }

  test("Put stores a value when empty") {
    val prog: Txn[String] = for {
      tmvar <- TMVar.empty[String]
      _     <- tmvar.put("hello")
      value <- tmvar.take
    } yield value

    for (value <- stm.commit(prog)) yield assertEquals(value, "hello")
  }

  test("TryPut returns true when empty") {
    val prog: Txn[Boolean] = for {
      tmvar  <- TMVar.empty[String]
      result <- tmvar.tryPut("hello")
    } yield result

    for (value <- stm.commit(prog)) yield assert(value)
  }

  test("TryPut returns false when not empty") {
    val prog: Txn[Boolean] = for {
      tmvar  <- TMVar.of("world")
      result <- tmvar.tryPut("hello")
    } yield result

    for (value <- stm.commit(prog)) yield assert(!value)
  }

  test("IsEmpty is false when not empty") {
    val prog: Txn[Boolean] = for {
      tmvar <- TMVar.of("world")
      empty <- tmvar.isEmpty
    } yield empty

    for (value <- stm.commit(prog)) yield assert(!value)
  }

  test("IsEmpty is true when empty") {
    val prog: Txn[Boolean] = for {
      tmvar <- TMVar.empty[String]
      empty <- tmvar.isEmpty
    } yield empty

    for (value <- stm.commit(prog)) yield assert(value)
  }
}
