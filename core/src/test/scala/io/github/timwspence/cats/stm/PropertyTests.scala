package io.github.timwspence.cats.stm

import cats.implicits._

import cats.effect.IO

import munit.{CatsEffectSuite, ScalaCheckEffectSuite}

import org.scalacheck.effect.PropF

import org.scalacheck._

import scala.util.Random

/**
  * Test that concurrently executing transactions behave correctly.
  * We do this by setting up a list of TVar[Long] and a set of
  * STM transactions which subtracts an amount from one tvar and
  * adds the same amount to another tvar. The sum of the tvar values
  * should be invariant under the execution of all these transactions.
  */
class MaintainsInvariants extends CatsEffectSuite with ScalaCheckEffectSuite {

  val tvarGen: Gen[TVar[Long]] = for {
    value <- Gen.posNum[Long]
  } yield TVar.of(value).atomically[IO].unsafeRunSync

  def txnGen(count: TVar[Int]): List[TVar[Long]] => Gen[STM[Unit]] = tvars =>
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
    count <- Gen.const(TVar.of(0).atomically[IO].unsafeRunSync)
    total = tvars.foldLeft(0L)((acc, tvar) => acc + tvar.value)
    txns <- Gen.listOf(txnGen(count)(tvars))
    commit = txns.traverse(_.atomically[IO].start)
    run    = commit.flatMap(l => l.traverse(_.join)).void
    numTxns = txns.length
    c  = count.get.atomically[IO].map((_, numTxns))
  } yield (total, tvars, run, c)

  test("Transactions maintain invariants") {
    PropF
      .forAllF(gen) { g =>
        val total = g._1
        val tvars = g._2
        val txn   = g._3
        val count = g._4

        txn.flatMap { _ =>
          count.map { res =>
            assertEquals(tvars.map(_.value).sum, total)
            assertEquals(res._1, res._2)
          }
        }
      }
  }

}
