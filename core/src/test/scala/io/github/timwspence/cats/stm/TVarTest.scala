package io.github.timwspence.cats.stm

import cats.effect.IO

import munit.CatsEffectSuite

class TVarTest extends CatsEffectSuite {

  test("Get returns current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO]) yield assertEquals(value, "hello")
  }

  test("Set changes current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.set("world")
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO]) yield assertEquals(value, "world")
  }

  test("Modify changes current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO]) yield assertEquals(value, "HELLO")
  }

  test("Pending transaction is removed on success") {
    val tvar = TVar.of("foo").commit[IO].unsafeRunSync

    val prog: STM[String] = for {
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO]) yield {
      assertEquals(value, "FOO")

      assertEquals(tvar.value, "FOO")
      assert(tvar.pending.get.isEmpty)
    }
  }

  test("Pending transaction is removed on failure") {
    val tvar = TVar.of("foo").commit[IO].unsafeRunSync

    val prog: STM[String] = for {
      _     <- tvar.modify(_.toUpperCase)
      _     <- STM.abort[String](new RuntimeException("boom"))
      value <- tvar.get
    } yield value

    for (_ <- prog.commit[IO].attempt) yield {
      assertEquals(tvar.value, "foo")

      assert(tvar.pending.get.isEmpty)
    }
  }
}
