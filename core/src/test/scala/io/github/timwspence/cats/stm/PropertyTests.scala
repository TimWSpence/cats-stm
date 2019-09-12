package io.github.timwspence.cats.stm

import cats.effect.{ContextShift, IO, Timer}
import cats.instances.list._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.scalacheck._
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext
import scala.util.Random

/**
  * Test that concurrently executing transactions behave correctly.
  * We do this by setting up a list of TVar[Long] and a set of
  * STM transactions which subtracts an amount from one tvar and
  * adds the same amount to another tvar. The sum of the tvar values
  * should be invariant under the execution of all these transactions.
  */
class MaintainsInvariants extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with Matchers {
  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  implicit val timer: Timer[IO] = IO.timer(executionContext)

  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

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
    forAll(gen) { g =>
      val total = g._1
      val tvars = g._2
      val txn   = g._3

      txn.unsafeRunSync()

      tvars.map(_.value).sum shouldBe total
    }
  }

}
