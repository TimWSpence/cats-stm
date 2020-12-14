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

import cats.effect.Concurrent

/**
  * Convenience definition providing `MVar`-like behaviour
  * in the `STM` monad. That is, a `TMVar` is a mutable memory
  * location which is either empty or contains a value.
  *
  * Analogous to `cats.effect.concurrent.MVar`.
  */
trait TMVarLike[F[_]] extends STMLike[F] {
  final class TMVar[A] private[stm] (private val tvar: TVar[Option[A]]) {

    /**
      * Store a value. Retries if the `TMVar` already
      * contains a value.
      */
    def put(a: A): Txn[Unit] =
      tvar.get.flatMap {
        case Some(_) => retry
        case None    => tvar.set(Some(a))
      }

    /**
      * Read the current value. Retries if empty.
      */
    def read: Txn[A] =
      tvar.get.flatMap {
        case Some(value) => pure(value)
        case None        => retry
      }

    /**
      * Read the current value and empty it at the same
      * time. Retries if empty.
      */
    def take: Txn[A] =
      tvar.get.flatMap {
        case Some(value) => tvar.set(None) >> pure(value)
        case None        => retry
      }

    /**
      * Try to store a value. Returns `false` if put failed,
      * `true` otherwise.
      */
    def tryPut(a: A): Txn[Boolean] =
      tvar.get.flatMap {
        case Some(_) => pure(false)
        case None    => tvar.set(Some(a)) >> pure(true)
      }

    /**
      * Try to read the current value. Returns `None` if
      * empty, `Some(current)` otherwise.
      */
    def tryRead: Txn[Option[A]] = tvar.get

    /**
      * Try to take the current value. Returns `None` if
      * empty, `Some(current)` otherwise.
      */
    def tryTake: Txn[Option[A]] =
      tvar.get.flatMap {
        case v @ Some(_) => tvar.set(None) >> pure(v)
        case None        => pure(None)
      }

    /**
      * Check if currently empty.
      */
    def isEmpty: Txn[Boolean] = tryRead.map(_.isEmpty)

  }

  object TMVar {

    /**
      * Create a new `TMVar`, initialized with a value.
      */
    def of[A](value: A)(implicit F: Concurrent[F]): Txn[TMVar[A]] = make(Some(value))

    /**
      * Create a new empty `TMVar`.
      */
    def empty[A](implicit F: Concurrent[F]): Txn[TMVar[A]] = make(None)

    private def make[A](value: Option[A])(implicit F: Concurrent[F]): Txn[TMVar[A]] =
      TVar.of(value).map(tvar => new TMVar[A](tvar))

  }

}
