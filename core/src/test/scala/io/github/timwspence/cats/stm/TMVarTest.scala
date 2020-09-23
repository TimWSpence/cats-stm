package io.github.timwspence.cats.stm

import cats.effect.IO

import munit.CatsEffectSuite

class TMVarTest extends CatsEffectSuite {

  test("Read returns current value when not empty") {
    val prog: STM[String] = for {
      tmvar <- TMVar.of("hello")
      value <- tmvar.read
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "hello")
  }

  test("Read does not modify value when not empty") {
    val prog: STM[String] = for {
      tmvar <- TMVar.of("hello")
      _     <- tmvar.read
      value <- tmvar.read
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "hello")
  }

  test("Take returns current value when not empty") {
    val prog: STM[String] = for {
      tmvar <- TMVar.of("hello")
      value <- tmvar.take
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "hello")
  }

  test("Take empties tmvar when not empty") {
    val prog: STM[Boolean] = for {
      tmvar <- TMVar.of("hello")
      _     <- tmvar.take
      empty <- tmvar.isEmpty
    } yield empty

    for (value <- prog.atomically[IO]) yield assert(value)
  }

  test("Put stores a value when empty") {
    val prog: STM[String] = for {
      tmvar <- TMVar.empty[String]
      _     <- tmvar.put("hello")
      value <- tmvar.take
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "hello")
  }

  test("TryPut returns true when empty") {
    val prog: STM[Boolean] = for {
      tmvar  <- TMVar.empty[String]
      result <- tmvar.tryPut("hello")
    } yield result

    for (value <- prog.atomically[IO]) yield assert(value)
  }

  test("TryPut returns false when not empty") {
    val prog: STM[Boolean] = for {
      tmvar  <- TMVar.of("world")
      result <- tmvar.tryPut("hello")
    } yield result

    for (value <- prog.atomically[IO]) yield assert(!value)
  }

  test("IsEmpty is false when not empty") {
    val prog: STM[Boolean] = for {
      tmvar <- TMVar.of("world")
      empty <- tmvar.isEmpty
    } yield empty

    for (value <- prog.atomically[IO]) yield assert(!value)
  }

  test("IsEmpty is true when empty") {
    val prog: STM[Boolean] = for {
      tmvar <- TMVar.empty[String]
      empty <- tmvar.isEmpty
    } yield empty

    for (value <- prog.atomically[IO]) yield assert(value)
  }
}
