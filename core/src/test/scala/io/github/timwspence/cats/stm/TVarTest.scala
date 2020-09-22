package io.github.timwspence.cats.stm

import cats.effect.IO

import munit.CatsEffectSuite

import scala.concurrent.duration._

class TVarTest extends CatsEffectSuite {

  test("Get returns current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      value <- tvar.get
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "hello")
  }

  test("Set changes current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.set("world")
      value <- tvar.get
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "world")
  }

  test("Modify changes current value") {
    val prog: STM[String] = for {
      tvar  <- TVar.of("hello")
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- prog.atomically[IO]) yield assertEquals(value, "HELLO")
  }

  test("Transaction is registered for retry") {
    val tvar = TVar.of(0).atomically[IO].unsafeRunSync

    val prog: IO[(Int, Int)] = for {
      fiber <- (for {
          current <- tvar.get
          _       <- STM.check(current > 0)
        } yield current).atomically[IO].start
      _ <- IO.sleep(1 second)
      pending = tvar.pending.get.size
      _   <- tvar.set(2).atomically[IO]
      res <- fiber.join
    } yield (pending, res)

    prog.map { res =>
      assertEquals(res._1, 1)
      assertEquals(res._2, 2)
    }

  }

  test("Transaction is re-registered for retry") {
    val tvar = TVar.of(0).atomically[IO].unsafeRunSync

    val prog: IO[(Int, Int, Int)] = for {
      fiber <- (for {
          current <- tvar.get
          _       <- STM.check(current > 1)
        } yield current).atomically[IO].start
      _ <- IO.sleep(1 second)
      pending = tvar.pending.get.size
      _ <- tvar.set(1).atomically[IO]
      _ <- IO.sleep(1 second)
      pending2 = tvar.pending.get.size
      _ <- tvar.set(2).atomically[IO]
      _ <- IO.sleep(1 second)
      pending3 = tvar.pending.get.size
      _ <- fiber.join
    } yield (pending, pending2, pending3)

    prog.map { res =>
      assertEquals(res._1, 1)
      assertEquals(res._2, 1)
      assertEquals(res._3, 0)
    }

  }

  test("Retry is unregistered on success") {
    val tvar = TVar.of(0).atomically[IO].unsafeRunSync

    val prog: IO[(Int, Int)] = for {
      fiber <- (for {
          current <- tvar.get
          _       <- STM.check(current > 0)
        } yield current).atomically[IO].start
      _ <- IO.sleep(1 second)
      pending = tvar.pending.get.size
      _ <- tvar.set(2).atomically[IO]
      _ <- fiber.join
      pending2 = tvar.pending.get.size
    } yield (pending, pending2)

    prog.map { res =>
      assertEquals(res._1, 1)
      assertEquals(res._2, 0)
    }

  }

  test("Pending transaction is removed on success") {
    val tvar = TVar.of("foo").atomically[IO].unsafeRunSync

    val prog: STM[String] = for {
      _     <- tvar.modify(_.toUpperCase)
      value <- tvar.get
    } yield value

    for (value <- prog.atomically[IO]) yield {
      assertEquals(value, "FOO")

      assertEquals(tvar.value, "FOO")
      assert(tvar.pending.get.isEmpty)
    }
  }

  test("Pending transaction is removed on failure") {
    val tvar = TVar.of("foo").atomically[IO].unsafeRunSync

    val prog: STM[String] = for {
      _     <- tvar.modify(_.toUpperCase)
      _     <- STM.abort[String](new RuntimeException("boom"))
      value <- tvar.get
    } yield value

    for (_ <- prog.atomically[IO].attempt) yield {
      assertEquals(tvar.value, "foo")

      assert(tvar.pending.get.isEmpty)
    }
  }

  test("TVar.of is referentially transparent") {
    val t: STM[TVar[Int]] = TVar.of(0)

    val prog = for {
      t1 <- t.atomically[IO]
      t2 <- t.atomically[IO]
      _ <- (for {
        _ <- t1.modify(_ + 1)
        _ <- t2.modify(_ + 2)
      } yield ()).atomically[IO]
      v1 <- t1.get.atomically[IO]
      v2 <- t2.get.atomically[IO]
    } yield (v1 -> v2)

    prog.map { res =>
      assertEquals(res._1, 1)
      assertEquals(res._2, 2)
    }
  }
}
