package io.github.timwspence.cats.stm

import cats.implicits._
import cats.kernel.laws.discipline._
import cats.laws.discipline._

import munit.DisciplineSuite

class CatsLawsSpec extends DisciplineSuite with Instances {
  checkAll("STM[Int]", SemigroupTests[STM[Int]].semigroup)

  checkAll("STM[Int]", MonoidTests[STM[Int]].monoid)

  checkAll("STM[Int]", FunctorTests[STM].functor[Int, Int, Int])

  checkAll("STM[Int]", ApplicativeTests[STM].applicative[Int, Int, Int])

  checkAll("STM[Int]", MonadTests[STM].monad[Int, Int, Int])

  checkAll("STM[Int]", SemigroupKTests[STM].semigroupK[Int])

  checkAll("STM[Int]", MonoidKTests[STM].monoidK[Int])
}
