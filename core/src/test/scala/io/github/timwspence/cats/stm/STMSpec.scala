/*
 * Copyright 2020 TimWSpence
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.timwspence.cats.stm

import scala.concurrent.duration._

import cats.effect.IO
import cats.implicits._
import munit.CatsEffectSuite
import scala.util.Random

/**
  * Basic tests for correctness in the absence of
  * (most) concurrency
  */
class SequentialTests extends CatsEffectSuite {

  val stm = STM[IO].unsafeRunSync()
  import stm._

  test("Basic transaction is executed") {
    val from = stm.commit(TVar.of(100)).unsafeRunSync()
    val to   = stm.commit(TVar.of(0)).unsafeRunSync()

    val prog = for {
      _ <- stm.commit {
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
    val from = stm.commit(TVar.of(100)).unsafeRunSync()
    val to   = stm.commit(TVar.of(0)).unsafeRunSync()

    val prog = for {
      _ <- stm.commit {
        for {
          balance <- from.get
          _       <- from.modify(_ - balance)
          _       <- stm.abort[Unit](new RuntimeException("Boom"))
        } yield ()
      }
    } yield ()

    for (_ <- prog.attempt) yield {
      assertEquals(from.value, 100)
      assertEquals(to.value, 0)
    }
  }

  test("Check retries until transaction succeeds") {
    val from         = stm.commit(TVar.of(100)).unsafeRunSync()
    val to           = stm.commit(TVar.of(0)).unsafeRunSync()
    var checkCounter = 0

    val prog = for {
      _ <- (for {
          _ <- IO.sleep(2 seconds)
          _ <- stm.commit(from.modify(_ + 1))
        } yield ()).start
      _ <- stm.commit {
        for {
          balance <- from.get
          _       <- { checkCounter += 1; stm.check(balance > 100) }
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

  test("Check retries repeatedly") {
    val tvar = stm.commit(TVar.of(0)).unsafeRunSync()

    val retry: Txn[Int] = for {
      current <- tvar.get
      _       <- stm.check(current > 10)
    } yield current

    val background: IO[Unit] = 1
      .to(11)
      .toList
      .traverse_(_ => stm.commit(tvar.modify(_ + 1)) >> IO.sleep(100.millis))

    val prog = for {
      fiber <- background.start
      res   <- stm.commit(retry)
      _     <- fiber.join
    } yield res

    prog.map { res =>
      assertEquals(res, 11)
    }

  }

  test("OrElse runs second transaction if first retries") {
    val account = stm.commit(TVar.of(100)).unsafeRunSync()

    val first = for {
      balance <- account.get
      _       <- stm.check(balance > 100)
      _       <- account.modify(_ - 100)
    } yield ()

    val second = for {
      balance <- account.get
      _       <- stm.check(balance > 50)
      _       <- account.modify(_ - 50)
    } yield ()

    val prog = for {
      _ <- stm.commit(first.orElse(second))
    } yield ()

    for (_ <- prog) yield assertEquals(account.value, 50)
  }

  test("OrElse reverts changes if retrying") {
    val account = stm.commit(TVar.of(100)).unsafeRunSync()

    val first = for {
      _ <- account.modify(_ - 100)
      _ <- stm.retry[Unit]
    } yield ()

    val second = for {
      balance <- account.get
      _       <- stm.check(balance > 50)
      _       <- account.modify(_ - 50)
    } yield ()

    val prog = for {
      _ <- stm.commit(first.orElse(second))
    } yield ()

    for (_ <- prog) yield assertEquals(account.value, 50)
  }

  test("OrElse reverts changes to tvars not previously modified if retrying") {
    val account = stm.commit(TVar.of(100)).unsafeRunSync()
    val other   = stm.commit(TVar.of(100)).unsafeRunSync()

    val first = for {
      _ <- other.modify(_ - 100)
      _ <- stm.retry[Unit]
    } yield ()

    val second = for {
      balance <- account.get
      _       <- stm.check(balance > 50)
      _       <- account.modify(_ - 50)
    } yield ()

    val prog = for {
      _ <- stm.commit {
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

  test("nested orElse") {
    val tvar = stm.commit(TVar.of(100)).unsafeRunSync()

    val first = for {
      _ <- tvar.modify(_ - 100)
      _ <- stm.retry[Unit]
    } yield ()

    val second = for {
      _       <- tvar.modify(_ - 10)
      balance <- tvar.get
      _       <- stm.check(balance == 50)
      _       <- tvar.modify(_ - 50)
    } yield ()

    val third = for {
      balance <- tvar.get
      _       <- stm.check(balance == 100)
      _       <- tvar.modify(_ - 50)
    } yield ()

    val prog = stm.commit((first.orElse(second).orElse(third) >> tvar.get))

    prog.map { res =>
      assertEquals(res, 50)
    }
  }

  test("Transaction is retried if TVar in if branch is subsequently modified") {
    val tvar = stm.commit(TVar.of(0L)).unsafeRunSync()

    val retry: Txn[Unit] = for {
      current <- tvar.get
      _       <- stm.check(current > 0)
      _       <- tvar.modify(_ + 1)
    } yield ()

    val background: IO[Unit] =
      for {
        _ <- IO.sleep(2 seconds)
        _ <- stm.commit(tvar.modify(_ + 1))
      } yield ()

    val prog = for {
      fiber <- background.start
      _     <- stm.commit(retry.orElse(stm.retry))
      _     <- fiber.join
    } yield ()

    for (_ <- prog) yield assertEquals(tvar.value, 2L)

    // assert(tvar.pending.get.isEmpty)
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
    val flag = stm.commit(TVar.of(false)).unsafeRunSync()
    val tvar = stm.commit(TVar.of(0L)).unsafeRunSync()

    val retry: IO[Unit] = stm.commit {
      for {
        current <- flag.get
        _       <- stm.check(current)
        _       <- tvar.modify(_ + 1)
      } yield ()
    }

    val background: IO[Unit] =
      for {
        _ <- IO.sleep(2 seconds)
        _ <- stm.commit(flag.set(true))
      } yield ()

    val prog = for {
      fiber <- background.start
      ret1  <- retry.start
      ret2  <- retry.start
      _     <- fiber.join
      _     <- ret1.join
      _     <- ret2.join
    } yield ()

    for (_ <- prog) yield assertEquals(tvar.value, 2L)
  }

  test("Atomically is referentially transparent 2") {
    val tvar = stm.commit(TVar.of(0L)).unsafeRunSync()

    val inc: IO[Unit] = stm.commit(tvar.modify(_ + 1))

    val prog = inc >> inc >> inc >> inc >> inc >> stm.commit(tvar.get)

    prog.map { res =>
      assertEquals(res, 5L)
    }
  }

  test("Modify is referentially transparent 2") {
    val tvar = stm.commit(TVar.of(0L)).unsafeRunSync()

    val inc: Txn[Unit] = tvar.modify(_ + 1)

    val prog = stm.commit(inc >> inc >> inc >> inc >> inc >> tvar.get)

    prog.map { res =>
      assertEquals(res, 5L)
    }
  }

  test("stack-safe construction") {
    val tvar       = stm.commit(TVar.of(0L)).unsafeRunSync()
    val iterations = 100000

    IO.pure(
      1.to(iterations).foldLeft(stm.unit) { (prog, _) =>
        prog >> tvar.modify(_ + 1)
      }
    )

  }

  test("stack-safe evaluation") {
    val tvar       = stm.commit(TVar.of(0)).unsafeRunSync()
    val iterations = 100000

    stm
      .commit(
        1.to(iterations).foldLeft(stm.unit) { (prog, _) =>
          prog >> tvar.modify(_ + 1)
        } >> tvar.get
      )
      .map { res =>
        assertEquals(res, iterations)
      }

  }

  test("race retrying fiber and fiber which unblocks it") {
    val iterations = 100

    List.range(0, iterations).traverse { _ =>
      for {
        tvar <- stm.commit(TVar.of(0))
        unblock = stm.commit(tvar.modify(_ + 1))
        retry = stm.commit(
          for {
            current <- tvar.get
            _       <- stm.check(current == 1)
            _       <- tvar.set(current + 1)
          } yield ()
        )
        _   <- IO.both(retry, unblock).timeout(2.seconds)
        v   <- stm.commit(tvar.get)
        res <- IO(assertEquals(v, 2))
      } yield res
    }
  }

  test("SO much contention and retrying") {
    for {
      tvar <- stm.commit(TVar.of(0))
      fs <- Random.shuffle(List.range(0, 100)).parTraverse { n =>
        stm
          .commit(
            for {
              current <- tvar.get
              _       <- stm.check(current == n)
              _       <- tvar.set(current + 1)
            } yield ()
          )
          .start
      }
      _   <- fs.traverse_(_.join)
      v   <- stm.commit(tvar.get)
      res <- IO(assertEquals(v, 100))
    } yield res
  }

  test("unblock all transactions") {
    for {
      tvar1 <- stm.commit(TVar.of(0))
      tvar2 <- stm.commit(TVar.of(0))
      f1 <-
        stm
          .commit(
            for {
              current <- tvar1.get
              _       <- stm.check(current == 1)
              _       <- tvar1.set(2)
            } yield current
          )
          .start
      f2 <-
        stm
          .commit(
            for {
              current <- tvar2.get
              _       <- stm.check(current == 1)
              _       <- tvar2.set(2)
            } yield current
          )
          .start
      _  <- stm.commit(tvar1.set(1) >> tvar2.set(1))
      _  <- f1.join.timeout(1.second)
      _  <- f2.join.timeout(1.second)
      v1 <- stm.commit(tvar1.get)
      v2 <- stm.commit(tvar2.get)
      res <- IO {
        assertEquals(v1 -> v2, 2 -> 2)
      }
    } yield res
  }

}
