package io.github.timwspence.cats.stm

import cats.implicits._

import cats.effect.IO

import munit.CatsEffectSuite

class TQueueTest extends CatsEffectSuite {

  test("Read removes the first element") {
    val prog: STM[(String, Boolean)] = for {
      tqueue <- TQueue.empty[String]
      _      <- tqueue.put("hello")
      value  <- tqueue.read
      empty  <- tqueue.isEmpty
    } yield value -> empty

    for (value <- prog.atomically[IO]) yield {
      assertEquals(value._1, "hello")
      assert(value._2)
    }
  }

  test("Peek does not remove the first element") {
    val prog: STM[(String, Boolean)] = for {
      tqueue <- TQueue.empty[String]
      _      <- tqueue.put("hello")
      value  <- tqueue.peek
      empty  <- tqueue.isEmpty
    } yield value -> empty

    for (value <- prog.atomically[IO]) yield {
      assertEquals(value._1, "hello")
      assert(!value._2)
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

    for (value <- prog.atomically[IO]) yield assertEquals(value, "helloworld")
  }

}
