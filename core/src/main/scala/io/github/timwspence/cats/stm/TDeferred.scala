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

import cats.{Contravariant, Functor, Invariant}

trait TDeferredLike[F[_]] extends STMLike[F] {

  sealed trait TDeferredSource[+A] { self =>
    def get: Txn[A]
    def tryGet: Txn[Option[A]]
    def map[B](f: A => B): TDeferredSource[B] =
      new TDeferredSource[B] {
        final override def get: Txn[B] =
          self.get.map(f)
        final override def tryGet: Txn[Option[B]] =
          self.tryGet.map(_.map(f))
      }
  }

  final object TDeferredSource {
    implicit final def functorForTDeferredSource: Functor[TDeferredSource] =
      new Functor[TDeferredSource] {
        final override def map[A, B](td: TDeferredSource[A])(f: A => B): TDeferredSource[B] =
          td.map(f)
      }
  }

  sealed trait TDeferredSink[-A] { self =>
    def complete(a: A): Txn[Boolean]
    def contramap[B](f: B => A): TDeferredSink[B] =
      new TDeferredSink[B] {
        final override def complete(b: B): Txn[Boolean] =
          self.complete(f(b))
      }
  }

  final object TDeferredSink {
    implicit final def contravariantForTDeferredSink: Contravariant[TDeferredSink] =
      new Contravariant[TDeferredSink] {
        final override def contramap[A, B](td: TDeferredSink[A])(f: B => A): TDeferredSink[B] =
          td.contramap(f)
      }
  }

  sealed abstract class TDeferred[A] extends TDeferredSource[A] with TDeferredSink[A] {
    def imap[B](f: A => B)(g: B => A): TDeferred[B] =
      new TDeferred.MappedTDeferred[A, B](this)(f)(g)
  }

  final object TDeferred {

    final def apply[A]: Txn[TDeferred[A]] =
      TVar.of[Option[A]](None).map(new TDeferredImpl(_))

    implicit final def invariantForTDeferred: Invariant[TDeferred] =
      new Invariant[TDeferred] {
        final override def imap[A, B](td: TDeferred[A])(f: A => B)(g: B => A): TDeferred[B] =
          td.imap(f)(g)
      }

    final private class TDeferredImpl[A](repr: TVar[Option[A]]) extends TDeferred[A] {

      final override def get: Txn[A] =
        repr.get.flatMap {
          case Some(a) => pure(a)
          case None    => retry
        }

      final override def tryGet: Txn[Option[A]] =
        repr.get

      final override def complete(a: A): Txn[Boolean] =
        repr.get.flatMap {
          case Some(_) => pure(false)
          case None    => repr.set(Some(a)).as(true)
        }
    }

    final private class MappedTDeferred[A, B](self: TDeferred[A])(f: A => B)(g: B => A) extends TDeferred[B] {

      final override def get: Txn[B] =
        self.get.map(f)

      final override def tryGet: Txn[Option[B]] =
        self.tryGet.map(_.map(f))

      final override def complete(b: B): Txn[Boolean] =
        self.complete(g(b))
    }
  }
}
