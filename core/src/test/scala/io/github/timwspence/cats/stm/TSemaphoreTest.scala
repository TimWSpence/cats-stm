package io.github.timwspence.cats.stm

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.{AsyncFunSuite, Matchers}

import scala.concurrent.ExecutionContext

class TSemaphoreTest extends AsyncFunSuite with Matchers {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  implicit val timer: Timer[IO] = IO.timer(executionContext)

  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  test("Acquire decrements the number of permits") {
    val prog: STM[Long] = for {
      tsem  <- TSemaphore.make(1)
      _     <- tsem.acquire
      value <- tsem.available
    } yield value

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe 0
    }
  }

  test("Release increments the number of permits") {
    val prog: STM[Long] = for {
      tsem  <- TSemaphore.make(0)
      _     <- tsem.release
      value <- tsem.available
    } yield value

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe 1
    }
  }

}
