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

import scala.util.Random

import cats.effect.IO
import cats.implicits._
import munit.ScalaCheckEffectSuite
import org.scalacheck._
import org.scalacheck.effect.PropF

/** Test that concurrently executing transactions behave correctly. We do this by setting up a list of TVar[Long] and a
  * set of STM transactions which subtracts an amount from one tvar and adds the same amount to another tvar. The sum of
  * the tvar values should be invariant under the execution of all these transactions.
  */
//TODO cross-build this for ScalaJS
class MaintainsInvariants extends BaseSpec with ScalaCheckEffectSuite {

  test("Transactions maintain invariants") {
    val stm = STM.runtime[IO].unsafeRunSync()
    import stm._

    val tvarGen: Gen[TVar[Long]] = for {
      value <- Gen.posNum[Long]
    } yield stm.commit(TVar.of(value)).unsafeRunSync()

    def txnGen(count: TVar[Int]): List[TVar[Long]] => Gen[Txn[Unit]] =
      tvars =>
        for {
          fromIdx <- Gen.choose(0, tvars.length - 1)
          toIdx   <- Gen.choose(0, tvars.length - 1) suchThat (_ != fromIdx)
          txn <- for {
            balance <- tvars(fromIdx).get
            transfer = Math.abs(Random.nextLong()) % balance
            _ <- tvars(fromIdx).modify(_ - transfer)
            _ <- tvars(toIdx).modify(_ + transfer)
          } yield ()
          _ <- count.modify(_ + 1)
        } yield txn

    val gen: Gen[(Long, List[TVar[Long]], IO[Unit], IO[(Int, Int)])] = for {
      tvars <- Gen.listOfN(50, tvarGen)
      count <- Gen.const(stm.commit(TVar.of(0)).unsafeRunSync())
      total = tvars.foldM(0L)((acc, tvar) => stm.commit(tvar.get).map(acc + _)).unsafeRunSync()
      txns <- Gen.listOf(txnGen(count)(tvars))
      commit  = txns.traverse(t => stm.commit(t).start)
      run     = commit.flatMap(l => l.traverse(_.join)).void
      numTxns = txns.length
      c       = stm.commit(count.get).map((_, numTxns))
    } yield (total, tvars, run, c)
    PropF
      .forAllF(gen) { g =>
        val total = g._1
        val tvars = g._2
        val txn   = g._3
        val count = g._4

        txn.flatMap { _ =>
          count.flatMap { res =>
            tvars.foldM(0L)((acc, tvar) => stm.commit(tvar.get).map(acc + _)).flatMap { newTotal =>
              IO {
                assertEquals(newTotal, total)
                assertEquals(res._1, res._2)
              }

            }
          }
        }

      }
  }

}
