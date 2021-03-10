/*
 * Copyright 2017-2021 TimWSpence
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

import java.util.concurrent.TimeUnit

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._

/**
  * To do comparative benchmarks between versions:
  *
  *     benchmarks/run-benchmark TxnBenchmark
  *
  * This will generate results in `benchmarks/results`.
  *
  * Or to run the benchmark from within sbt:
  *
  *     benchmarks/jmh:run -i 10 -wi 10 -f 2 -t 1 io.github.timwspence.cats.stm.TxnBenchmark
  *
  * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread".
  * Please note that benchmarks should be usually executed at least in
  * 10 iterations (as a rule of thumb), but more is better.
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TxnBenchmark {

  val stm = STM.runtime[IO].unsafeRunSync()
  import stm._

  @Param(Array("1000"))
  var size: Int = _

  @Benchmark
  def get() = {
    def loop(tvar: TVar[Int], i: Int): IO[Unit] =
      if (i < size) stm.commit(tvar.get) >> loop(tvar, i + 1)
      else IO.unit

    stm
      .commit(TVar.of(0))
      .flatMap { tvar =>
        loop(tvar, 0)
      }
      .unsafeRunSync()
  }

  @Benchmark
  def set() = {
    def loop(tvar: TVar[Int], i: Int): IO[Unit] =
      if (i < size) stm.commit(tvar.set(i)) >> loop(tvar, i + 1)
      else IO.unit

    stm
      .commit(TVar.of(0))
      .flatMap { tvar =>
        loop(tvar, 0)
      }
      .unsafeRunSync()
  }

  @Benchmark
  def bind() = {
    def loop(tvar: TVar[Int], i: Int): Txn[Unit] =
      if (i < size) tvar.set(i) >> loop(tvar, i + 1)
      else stm.unit

    stm
      .commit(TVar.of(0))
      .flatMap { tvar =>
        stm.commit(loop(tvar, 0))
      }
      .unsafeRunSync()
  }

  @Benchmark
  def orElse() = {
    def loop(tvar: TVar[Int], i: Int): IO[Unit] =
      if (i < size) stm.commit(stm.retry.orElse(tvar.set(i))) >> loop(tvar, i + 1)
      else IO.unit

    stm
      .commit(TVar.of(0))
      .flatMap { tvar =>
        loop(tvar, 0)
      }
      .unsafeRunSync()
  }

}
