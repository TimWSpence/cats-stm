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

trait TDeferredLike[F[_]] extends STMLike[F] {

  final class TDeferred[A] private (repr: TVar[Option[A]]) {

    final def get: Txn[A] =
      repr.get.flatMap {
        case Some(a) => pure(a)
        case None    => retry
      }

    final def tryGet: Txn[Option[A]] =
      repr.get

    final def complete(a: A): Txn[Boolean] =
      repr.get.flatMap {
        case Some(_) => pure(false)
        case None    => repr.set(Some(a)).as(true)
      }
  }

  final object TDeferred {
    final def apply[A]: Txn[TDeferred[A]] =
      TVar.of[Option[A]](None).map(new TDeferred(_))
  }
}
