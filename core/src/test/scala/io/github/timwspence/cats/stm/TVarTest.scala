package io.github.timwspence.cats.stm

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AsyncFunSuite 

import scala.concurrent.ExecutionContext

class TVarTest extends AsyncFunSuite with Matchers {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  implicit val timer: Timer[IO] = IO.timer(executionContext)

  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  test("Get returns current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe "hello"
    }
  }

  test("Set changes current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.set("world")
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe "world"
      value shouldBe "world"
    }
  }

  test("Modify changes current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe "HELLO"
    }
  }

  test("Pending transaction is removed on success") {
    val tvar = TVar.of("foo").commit[IO].unsafeRunSync

    val prog: STM[String] = for {
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe "FOO"

      tvar.value shouldBe "FOO"
      tvar.pending.get.isEmpty shouldBe true
    }
  }

  test("Pending transaction is removed on failure") {
    val tvar = TVar.of("foo").commit[IO].unsafeRunSync

    val prog: STM[String] = for {
      _     <- tvar.modify(_.toUpperCase)
      _     <- STM.abort[String](new RuntimeException("boom"))
      value <- tvar.get
    } yield value

    for (_ <- prog.commit[IO].attempt.unsafeToFuture) yield {
      tvar.value shouldBe "foo"

      tvar.pending.get.isEmpty shouldBe true
    }
  }
}
