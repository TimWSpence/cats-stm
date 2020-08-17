package io.github.timwspence.cats.stm

import cats.implicits._

import cats.effect.IO

//TODO replace this with ScalaCheckEffectSuite and remove the `.check()`
//once it is released
import munit.{CatsEffectSuite, ScalaCheckSuite}

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
class MaintainsInvariants extends CatsEffectSuite with ScalaCheckSuite {

  val tvarGen: Gen[TVar[Long]] = for {
    value <- Gen.posNum[Long]
  } yield TVar.of(value).commit[IO].unsafeRunSync

  val txnGen: List[TVar[Long]] => Gen[STM[Unit]] = tvars =>
    for {
      fromIdx <- Gen.choose(0, tvars.length - 1)
      toIdx   <- Gen.choose(0, tvars.length - 1) suchThat (_ != fromIdx)
      txn <- for {
        balance <- tvars(fromIdx).get
        transfer = Math.abs(Random.nextLong()) % balance
        _ <- tvars(fromIdx).modify(_ - transfer)
        _ <- tvars(toIdx).modify(_ + transfer)
      } yield ()
    } yield txn

  val gen: Gen[(Long, List[TVar[Long]], IO[Unit])] = for {
    tvars <- Gen.listOfN(50, tvarGen)
    total = tvars.foldLeft(0L)((acc, tvar) => acc + tvar.value)
    txns <- Gen.listOf(txnGen(tvars))
    commit = txns.traverse(_.commit[IO].start)
    run    = commit.flatMap(l => l.traverse(_.join)).void
  } yield (total, tvars, run)

  test("Transactions maintain invariants") {
    PropF
      .forAllF(gen) { g =>
        val total = g._1
        val tvars = g._2
        val txn   = g._3

        txn.map { _ =>
          assertEquals(tvars.map(_.value).sum, total)
        }
      }
      .check()
  }

}
