package io.github.timwspence.cats.stm

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.{AsyncFunSuite, Matchers}

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
}
