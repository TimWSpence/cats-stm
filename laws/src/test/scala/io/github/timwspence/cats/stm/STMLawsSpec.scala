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

import munit.{CatsEffectSuite, DisciplineSuite}
import cats.effect.IO
import org.scalacheck.Arbitrary

class STMLawsSpec extends CatsEffectSuite with STMTests with Instances with DisciplineSuite with HasSTM {
  override val stm: STM[IO] = STM[IO].unsafeRunSync()
  import stm._

  implicitly[Arbitrary[Txn[Int]]]

  checkAll("stm", stmLaws[Int])

}
