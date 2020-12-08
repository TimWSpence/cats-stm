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

import cats.effect.IO
import cats.implicits._
import cats.kernel.laws.discipline._
import cats.laws.discipline._
import munit.DisciplineSuite

class CatsLawsSpec extends Instances with DisciplineSuite {

  override val stm: STM[IO] = STM.runtime[IO].unsafeRunSync()
  import stm._

  checkAll("Txn[Int]", MonoidTests[Txn[Int]].monoid)

  checkAll("Txn[Int]", MonadErrorTests[Txn, Throwable].monadError[Int, Int, Int])

  checkAll("Txn[Int]", MonoidKTests[Txn].monoidK[Int])
}
