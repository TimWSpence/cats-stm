package io.github.timwspence.cats.stm

import cats.effect.IO

import munit.CatsEffectSuite

class TSemaphoreTest extends CatsEffectSuite {

  test("Acquire decrements the number of permits") {
    val prog: STM[Long] = for {
      tsem  <- TSemaphore.make(1)
      _     <- tsem.acquire
      value <- tsem.available
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, 0L)
  }

  test("Release increments the number of permits") {
    val prog: STM[Long] = for {
      tsem  <- TSemaphore.make(0)
      _     <- tsem.release
      value <- tsem.available
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, 1L)
  }

}
