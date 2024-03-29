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

import cats.effect.IO
import cats.implicits._

class TQueueSpec extends BaseSpec {

  stmTest("Read removes the first element") { stm =>
    import stm._
    for {
      v <- stm.commit(for {
        tqueue <- TQueue.empty[String]
        _      <- tqueue.put("hello")
        value  <- tqueue.read
        empty  <- tqueue.isEmpty
      } yield value -> empty)
      res <- IO {
        assertEquals(v._1, "hello")
        assert(v._2)
      }
    } yield res
  }

  stmTest("Peek does not remove the first element") { stm =>
    import stm._
    for {
      v <- stm.commit(
        for {
          tqueue <- TQueue.empty[String]
          _      <- tqueue.put("hello")
          value  <- tqueue.peek
          empty  <- tqueue.isEmpty
        } yield value -> empty
      )
      res <- IO {
        assertEquals(v._1, "hello")
        assert(!v._2)
      }
    } yield res

  }

  stmTest("TQueue is FIFO") { stm =>
    import stm._
    for {
      v <- stm.commit(
        for {
          tqueue <- TQueue.empty[String]
          _      <- tqueue.put("hello")
          _      <- tqueue.put("world")
          hello  <- tqueue.read
          world  <- tqueue.peek
        } yield hello |+| world
      )
      res <- IO {
        assertEquals(v, "helloworld")
      }
    } yield res
  }

}
