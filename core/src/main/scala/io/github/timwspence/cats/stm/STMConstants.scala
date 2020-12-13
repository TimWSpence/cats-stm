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

private[stm] object STMConstants {

  type T = Byte
  val PureT: T        = 0
  val AllocT: T       = 1
  val BindT: T        = 2
  val HandleErrorT: T = 3
  val GetT: T         = 4
  val ModifyT: T      = 5
  val OrElseT: T      = 6
  val AbortT: T       = 7
  val RetryT: T       = 8

}
