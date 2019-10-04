package io.github.timwspence.cats.stm

import cats.effect.{ContextShift, IO, Timer}
import cats.instances.string._
import cats.syntax.semigroup._
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AsyncFunSuite 

import scala.concurrent.ExecutionContext

class TQueueTest extends AsyncFunSuite with Matchers {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  implicit val timer: Timer[IO] = IO.timer(executionContext)

  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  test("Read removes the first element") {
    val prog: STM[(String, Boolean)] = for {
      tqueue <- TQueue.empty[String]
      _      <- tqueue.put("hello")
      value  <- tqueue.read
      empty  <- tqueue.isEmpty
    } yield value -> empty

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value._1 shouldBe "hello"
      value._2 shouldBe true
    }
  }

  test("Peek does not remove the first element") {
    val prog: STM[(String, Boolean)] = for {
      tqueue <- TQueue.empty[String]
      _      <- tqueue.put("hello")
      value  <- tqueue.peek
      empty  <- tqueue.isEmpty
    } yield value -> empty

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value._1 shouldBe "hello"
      value._2 shouldBe false
    }
  }

  test("TQueue is FIFO") {
    val prog: STM[String] = for {
      tqueue <- TQueue.empty[String]
      _      <- tqueue.put("hello")
      _      <- tqueue.put("world")
      hello  <- tqueue.read
      world  <- tqueue.peek
    } yield hello |+| world

    for (value <- prog.commit[IO].unsafeToFuture) yield {
      value shouldBe "helloworld"
    }
  }

}
