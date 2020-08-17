package io.github.timwspence.cats.stm

import cats.effect.{IO, Timer}

import munit.CatsEffectSuite

import scala.concurrent.duration._

/**
  * Basic tests for correctness in the absence of
  * (most) concurrency
  */
class SequentialTests extends CatsEffectSuite {

  test("Basic transaction is executed") {
    val from = TVar.of(100).commit[IO].unsafeRunSync
    val to   = TVar.of(0).commit[IO].unsafeRunSync

    val prog = for {
      _ <- STM.atomically[IO] {
        for {
          balance <- from.get
          _       <- from.modify(_ - balance)
          _       <- to.modify(_ + balance)
        } yield ()
      }
    } yield ()

    for (_ <- prog) yield {
      assertEquals(from.value, 0)
      assertEquals(to.value, 100)
    }
  }

  test("Abort primitive aborts whole transaction") {
    val from = TVar.of(100).commit[IO].unsafeRunSync
    val to   = TVar.of(0).commit[IO].unsafeRunSync

    val prog = for {
      _ <- STM.atomically[IO] {
        for {
          balance <- from.get
          _       <- from.modify(_ - balance)
          _       <- STM.abort[Unit](new RuntimeException("Boom"))
        } yield ()
      }
    } yield ()

    for (_ <- prog.attempt) yield {
      assertEquals(from.value, 100)
      assertEquals(to.value, 0)
    }
  }

  test("Check retries until transaction succeeds") {
    val from         = TVar.of(100).commit[IO].unsafeRunSync
    val to           = TVar.of(0).commit[IO].unsafeRunSync
    var checkCounter = 0

    val prog = for {
      _ <- (for {
          _ <- Timer[IO].sleep(2 seconds)
          _ <- from.modify(_ + 1).commit[IO]
        } yield ()).start
      _ <- STM.atomically[IO] {
        for {
          balance <- from.get
          _       <- { checkCounter += 1; STM.check(balance > 100) }
          _       <- from.modify(_ - 100)
          _       <- to.modify(_ + 100)
        } yield ()
      }
    } yield ()

    for (_ <- prog.attempt) yield {
      assertEquals(from.value, 1)
      assertEquals(to.value, 100)
      assert(checkCounter > 1)
    }
  }

  test("OrElse runs second transaction if first retries") {
    val account = TVar.of(100).commit[IO].unsafeRunSync

    val first = for {
      balance <- account.get
      _       <- STM.check(balance > 100)
      _       <- account.modify(_ - 100)
    } yield ()

    val second = for {
      balance <- account.get
      _       <- STM.check(balance > 50)
      _       <- account.modify(_ - 50)
    } yield ()

    val prog = for {
      _ <- first.orElse(second).commit[IO]
    } yield ()

    for (_ <- prog) yield assertEquals(account.value, 50)
  }

  test("OrElse reverts changes if retrying") {
    val account = TVar.of(100).commit[IO].unsafeRunSync

    val first = for {
      _ <- account.modify(_ - 100)
      _ <- STM.retry[Unit]
    } yield ()

    val second = for {
      balance <- account.get
      _       <- STM.check(balance > 50)
      _       <- account.modify(_ - 50)
    } yield ()

    val prog = for {
      _ <- first.orElse(second).commit[IO]
    } yield ()

    for (_ <- prog) yield assertEquals(account.value, 50)
  }

  test("OrElse reverts changes to tvars not previously modified if retrying") {
    val account = TVar.of(100).commit[IO].unsafeRunSync
    val other   = TVar.of(100).commit[IO].unsafeRunSync

    val first = for {
      _ <- other.modify(_ - 100)
      _ <- STM.retry[Unit]
    } yield ()

    val second = for {
      balance <- account.get
      _       <- STM.check(balance > 50)
      _       <- account.modify(_ - 50)
    } yield ()

    val prog = for {
      _ <- STM.atomically[IO] {
        for {
          _ <- first.orElse(second)
        } yield ()
      }
    } yield ()

    for (_ <- prog) yield {
      assertEquals(account.value, 50)
      assertEquals(other.value, 100)
    }
  }

  test("Transaction is retried if TVar in if branch is subsequently modified") {
    val tvar = TVar.of(0L).commit[IO].unsafeRunSync

    val retry: STM[Unit] = for {
      current <- tvar.get
      _       <- STM.check(current > 0)
      _       <- tvar.modify(_ + 1)
    } yield ()

    val background: IO[Unit] =
      for {
        _ <- Timer[IO].sleep(2 seconds)
        _ <- tvar.modify(_ + 1).commit[IO]
      } yield ()

    val prog = for {
      fiber <- background.start
      _     <- retry.orElse(STM.retry).commit[IO]
      _     <- fiber.join
    } yield ()

    for (_ <- prog) yield {
      assertEquals(tvar.value, 2L)

      assert(tvar.pending.get.isEmpty)
    }
  }

  /**
    *  This seemingly strange test guards against reintroducing the issue
    *  fixed in ad10e29ae38aa8b9507833fe84a68cf7961aac57
    *  (https://github.com/TimWSpence/cats-stm/pull/96) whereby
    *  atomically was not referentially transparent and would re-use tx ids
    *  which caused problems if two transactions produced by the same
    *  atomically invocation both needed to retry - they would have the same
    *  id and hence we would only register one to retry
    */
  test("Atomically is referentially transparent") {
    val flag = TVar.of(false).commit[IO].unsafeRunSync
    val tvar = TVar.of(0L).commit[IO].unsafeRunSync

    val retry: IO[Unit] = STM.atomically[IO] {
      for {
        current <- flag.get
        _       <- STM.check(current)
        _       <- tvar.modify(_ + 1)
      } yield ()
    }

    val background: IO[Unit] =
      for {
        _ <- Timer[IO].sleep(2 seconds)
        _ <- flag.set(true).commit[IO]
      } yield ()

    val prog = for {
      fiber <- background.start
      _     <- retry.start
      _     <- retry.start
      _     <- fiber.join
    } yield ()

    for (_ <- prog) yield assertEquals(tvar.value, 2L)
  }

}
