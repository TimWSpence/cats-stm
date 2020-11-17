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
