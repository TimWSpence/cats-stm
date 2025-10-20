/*
 * Copyright 2017 TimWSpence
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
import scala.util.Random

/** Basic tests for correctness in the absence of (most) concurrency
  */
class STMSpec extends BaseSpec {

  stmTest("Basic transaction is executed") { stm =>
    import stm._
    for {
      from <- stm.commit(TVar.of(100))
      to   <- stm.commit(TVar.of(0))
      _    <- stm.commit {
        for {
          balance <- from.get
          _       <- from.modify(_ - balance)
          _       <- to.modify(_ + balance)
        } yield ()
      }
      vs  <- stm.commit((from.get, to.get).tupled)
      res <- IO {
        assertEquals(vs._1, 0)
        assertEquals(vs._2, 100)
      }
    } yield res
  }

  stmTest("Abort primitive aborts whole transaction") { stm =>
    import stm._
    for {
      from <- stm.commit(TVar.of(100))
      to   <- stm.commit(TVar.of(0))
      _    <- stm.commit {
        for {
          balance <- from.get
          _       <- from.modify(_ - balance)
          _       <- stm.abort[Unit](new RuntimeException("Boom"))
        } yield ()
      }.attempt
      vs  <- stm.commit((from.get, to.get).tupled)
      res <- IO {
        assertEquals(vs._1, 100)
        assertEquals(vs._2, 0)
      }
    } yield res
  }

  stmTest("Check retries until transaction succeeds") { stm =>
    import stm._
    var checkCounter = 0

    for {
      from <- stm.commit(TVar.of(100))
      to   <- stm.commit(TVar.of(0))
      _    <- (for {
        _ <- IO.sleep(2.seconds)
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
      vs  <- stm.commit((from.get, to.get).tupled)
      res <- IO {
        assertEquals(vs._1, 1)
        assertEquals(vs._2, 100)
        assert(checkCounter > 1)
      }
    } yield res
  }

  stmTest("Check retries repeatedly") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0))

      retry =
        for {
          current <- tvar.get
          _       <- stm.check(current > 10)
        } yield current

      background =
        1
          .to(11)
          .toList
          .traverse_(_ => stm.commit(tvar.modify(_ + 1)) >> IO.sleep(100.millis))

      fiber <- background.start
      v     <- stm.commit(retry)
      _     <- fiber.join
      res   <- IO {
        assertEquals(v, 11)
      }
    } yield res

  }

  stmTest("OrElse runs second transaction if first retries") { stm =>
    import stm._
    for {
      account <- stm.commit(TVar.of(100))

      first =
        for {
          balance <- account.get
          _       <- stm.check(balance > 100)
          _       <- account.modify(_ - 100)
        } yield ()

      second =
        for {
          balance <- account.get
          _       <- stm.check(balance > 50)
          _       <- account.modify(_ - 50)
        } yield ()

      _   <- stm.commit(first.orElse(second))
      v   <- stm.commit(account.get)
      res <- IO(assertEquals(v, 50))
    } yield res
  }

  stmTest("OrElse reverts changes if retrying") { stm =>
    import stm._
    for {
      account <- stm.commit(TVar.of(100))

      first =
        for {
          _ <- account.modify(_ - 100)
          _ <- stm.retry[Unit]
        } yield ()

      second =
        for {
          balance <- account.get
          _       <- stm.check(balance > 50)
          _       <- account.modify(_ - 50)
        } yield ()

      _   <- stm.commit(first.orElse(second))
      v   <- stm.commit(account.get)
      res <- IO(assertEquals(v, 50))
    } yield res
  }

  stmTest("OrElse reverts changes to tvars not previously modified if retrying") { stm =>
    import stm._
    for {
      account <- stm.commit(TVar.of(100))
      other   <- stm.commit(TVar.of(100))

      first =
        for {
          _ <- other.modify(_ - 100)
          _ <- stm.retry[Unit]
        } yield ()

      second =
        for {
          balance <- account.get
          _       <- stm.check(balance > 50)
          _       <- account.modify(_ - 50)
        } yield ()

      _ <- stm.commit {
        for {
          _ <- first.orElse(second)
        } yield ()
      }
      vs  <- stm.commit((account.get, other.get).tupled)
      res <- IO {
        assertEquals(vs._1, 50)
        assertEquals(vs._2, 100)
      }
    } yield res
  }

  stmTest("nested orElse") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(100))
      first =
        for {
          _ <- tvar.modify(_ - 100)
          _ <- stm.retry[Unit]
        } yield ()

      second =
        for {
          _       <- tvar.modify(_ - 10)
          balance <- tvar.get
          _       <- stm.check(balance == 50)
          _       <- tvar.modify(_ - 50)
        } yield ()

      third =
        for {
          balance <- tvar.get
          _       <- stm.check(balance == 100)
          _       <- tvar.modify(_ - 50)
        } yield ()

      v   <- stm.commit(first.orElse(second).orElse(third) >> tvar.get)
      res <- IO(assertEquals(v, 50))

    } yield res
  }

  stmTest("Transaction is retried if TVar in if branch is subsequently modified") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0L))

      retry =
        for {
          current <- tvar.get
          _       <- stm.check(current > 0)
          _       <- tvar.modify(_ + 1)
        } yield ()

      background =
        for {
          _ <- IO.sleep(2.seconds)
          _ <- stm.commit(tvar.modify(_ + 1))
        } yield ()

      fiber <- background.start
      _     <- stm.commit(retry.orElse(stm.retry))
      _     <- fiber.join
      v     <- stm.commit(tvar.get)
      res   <- IO(assertEquals(v, 2L))

    } yield res
  }

  /** This seemingly strange test guards against reintroducing the issue fixed in
    * ad10e29ae38aa8b9507833fe84a68cf7961aac57 (https://github.com/TimWSpence/cats-stm/pull/96) whereby atomically was
    * not referentially transparent and would re-use tx ids which caused problems if two transactions produced by the
    * same atomically invocation both needed to retry - they would have the same id and hence we would only register one
    * to retry
    */
  stmTest("Commit is referentially transparent") { stm =>
    import stm._
    for {
      flag <- stm.commit(TVar.of(false))
      tvar <- stm.commit(TVar.of(0L))

      retry = stm.commit {
        for {
          current <- flag.get
          _       <- stm.check(current)
          _       <- tvar.modify(_ + 1)
        } yield ()
      }

      background =
        for {
          _ <- IO.sleep(2.seconds)
          _ <- stm.commit(flag.set(true))
        } yield ()

      fiber <- background.start
      ret1  <- retry.start
      ret2  <- retry.start
      _     <- fiber.join
      _     <- ret1.join
      _     <- ret2.join
      v     <- stm.commit(tvar.get)
      res   <- IO(assertEquals(v, 2L))
    } yield res
  }

  stmTest("commit is referentially transparent 2") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0L))
      inc = stm.commit(tvar.modify(_ + 1))
      v   <- inc >> inc >> inc >> inc >> inc >> stm.commit(tvar.get)
      res <- IO(assertEquals(v, 5L))
    } yield res
  }

  stmTest("Modify is referentially transparent 2") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0L))
      inc = tvar.modify(_ + 1)

      v   <- stm.commit(inc >> inc >> inc >> inc >> inc >> tvar.get)
      res <- IO(assertEquals(v, 5L))
    } yield res
  }

  stmTest("stack-safe construction") { stm =>
    import stm._
    val iterations = 100000
    stm.commit(TVar.of(0L)).flatMap { tvar =>
      IO.pure(
        1.to(iterations).foldLeft(stm.unit) { (prog, _) =>
          prog >> tvar.modify(_ + 1)
        }
      )
    }
  }

  stmTest("stack-safe evaluation") { stm =>
    import stm._
    val iterations = 100000

    for {
      tvar <- stm.commit(TVar.of(0))
      v    <-
        stm
          .commit(
            1.to(iterations).foldLeft(stm.unit) { (prog, _) =>
              prog >> tvar.modify(_ + 1)
            } >> tvar.get
          )
      res <- IO(assertEquals(v, iterations))

    } yield res
  }

  stmTest("race retrying fiber and fiber which unblocks it") { stm =>
    import stm._
    val iterations = 100

    List.range(0, iterations).traverse { _ =>
      for {
        tvar <- stm.commit(TVar.of(0))
        unblock = stm.commit(tvar.modify(_ + 1))
        retry   = stm.commit(
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

  stmTest("lots of contention and retrying") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0))
      fs   <- Random.shuffle(List.range(0, 100)).parTraverse { n =>
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

  stmTest("unblock all transactions") { stm =>
    import stm._
    for {
      tvar1 <- stm.commit(TVar.of(0))
      tvar2 <- stm.commit(TVar.of(0))
      f1    <-
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
      _   <- stm.commit(tvar1.set(1) >> tvar2.set(1))
      _   <- f1.join.timeout(1.second)
      _   <- f2.join.timeout(1.second)
      v1  <- stm.commit(tvar1.get)
      v2  <- stm.commit(tvar2.get)
      res <- IO {
        assertEquals(v1 -> v2, 2 -> 2)
      }
    } yield res
  }

  stmTest("loop retry and completion") { stm =>
    import stm._
    val iterations = 10
    for {
      tvar <- stm.commit(TVar.of(0))
      first = stm.commit(
        for {
          current <- tvar.get
          _       <- stm.check(current == 0)
          _       <- tvar.set(1)
        } yield ()
      )
      second = stm.commit(
        for {
          current <- tvar.get
          _       <- stm.check(current == 1)
          _       <- tvar.set(0)
        } yield ()
      )
      f1  <- List(1, iterations).foldLeft(IO.unit)((acc, _) => acc >> first).start
      f2  <- List(1, iterations).foldLeft(IO.unit)((acc, _) => acc >> second).start
      _   <- (f1.joinWithNever, f2.joinWithNever).tupled
      v   <- stm.commit(tvar.get)
      res <- IO(assertEquals(v, 0))
    } yield res
  }

  stmTest("handle error") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0))
      _    <- stm.commit(
        for {
          _ <- tvar.set(1)
          _ <- (tvar.modify(_ + 1) >> stm.abort(new RuntimeException("BOOM"))).handleErrorWith { case _ =>
            tvar.modify(_ + 2)
          }
        } yield ()
      )
      v   <- stm.commit(tvar.get)
      res <- IO(assertEquals(v, 3))
    } yield res
  }

  stmTest("handle nested errors") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0))
      txn = tvar.set(1) >> (
        // This change to the log should be discarded
        tvar.set(2) >>
          stm.unit.handleErrorWith(_ => tvar.modify(_ + 1)) >> stm.abort(new RuntimeException())
      ).handleErrorWith(_ => tvar.modify(_ + 2))
      _   <- stm.commit(txn)
      v   <- stm.commit(tvar.get)
      res <- IO(assertEquals(v, 3))
    } yield res

  }

  stmTest("handle nested retries") { stm =>
    import stm._
    for {
      tvar <- stm.commit(TVar.of(0))
      txn = tvar.set(1) >> (
        tvar.set(3) >> stm.unit.orElse(stm.abort(new RuntimeException())) >> stm.retry
      ).orElse(tvar.modify(_ + 1))
      _   <- stm.commit(txn)
      v   <- stm.commit(tvar.get)
      res <- IO(assertEquals(v, 2))
    } yield res

  }

  test("instantiate via Make") {
    STM.Make[IO].runtime.flatMap { stm =>
      import stm._
      for {
        from <- stm.commit(TVar.of(100))
        to   <- stm.commit(TVar.of(0))
        _    <- stm.commit {
          for {
            balance <- from.get
            _       <- from.modify(_ - balance)
            _       <- to.modify(_ + balance)
          } yield ()
        }
        vs  <- stm.commit((from.get, to.get).tupled)
        res <- IO {
          assertEquals(vs._1, 0)
          assertEquals(vs._2, 100)
        }
      } yield res
    }
  }

}
