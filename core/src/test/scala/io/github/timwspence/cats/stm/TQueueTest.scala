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

// /*
//  * Copyright 2020 TimWSpence
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package io.github.timwspence.cats.stm

// import cats.effect.IO
// import cats.implicits._
// import munit.CatsEffectSuite

// class TQueueTest extends CatsEffectSuite {

//   test("Read removes the first element") {
//     val prog: STM[(String, Boolean)] = for {
//       tqueue <- TQueue.empty[String]
//       _      <- tqueue.put("hello")
//       value  <- tqueue.read
//       empty  <- tqueue.isEmpty
//     } yield value -> empty

//     for (value <- prog.atomically[IO]) yield {
//       assertEquals(value._1, "hello")
//       assert(value._2)
//     }
//   }

//   test("Peek does not remove the first element") {
//     val prog: STM[(String, Boolean)] = for {
//       tqueue <- TQueue.empty[String]
//       _      <- tqueue.put("hello")
//       value  <- tqueue.peek
//       empty  <- tqueue.isEmpty
//     } yield value -> empty

//     for (value <- prog.atomically[IO]) yield {
//       assertEquals(value._1, "hello")
//       assert(!value._2)
//     }
//   }

//   test("TQueue is FIFO") {
//     val prog: STM[String] = for {
//       tqueue <- TQueue.empty[String]
//       _      <- tqueue.put("hello")
//       _      <- tqueue.put("world")
//       hello  <- tqueue.read
//       world  <- tqueue.peek
//     } yield hello |+| world

//     for (value <- prog.atomically[IO]) yield assertEquals(value, "helloworld")
//   }

// }
