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

import cats.effect.std.Semaphore
import cats.effect.{Async, Deferred, Ref}
import cats.implicits._

trait STM[F[_]] extends STMLike[F] with TMVarLike[F] with TQueueLike[F] with TSemaphoreLike[F] {}

object STM {
  def apply[F[_]](implicit F: Async[F]): F[STM[F]] =
    for {
      idGen       <- Ref.of[F, Long](0)
      rateLimiter <- Semaphore[F](1) //TODO increase concurrency here
    } yield new STM[F] {
      import Internals._

      def commit[A](txn: Txn[A]): F[A] =
        for {
          _      <- F.delay(println("starting"))
          signal <- Deferred[F, Unit]
          p      <- rateLimiter.permit.use(_ => eval(idGen, txn))
          // p      <- eval(idGen, txn)
          (res, log) = p
          r <- res match {
            case TSuccess(a) =>
              for {
                _ <- log.debug //
                retryImmediately <- log.withLock(
                  F.ifM(log.isDirty)(
                    F.pure(true),
                    // F.delay(println(s"committing ${log.values.map(e => e.tvar.value -> e.initial -> e.current)}")) >> log.commit.as(
                    //   true
                    // )
                    F.delay(println("committing")) >> log.debug >> log.commit
                      .as(false) >> F.delay(println("committed")).as(false)
                  )
                )
                r <-
                  //TODO we arguably want an uncancelable joining signal to commit so we don't
                  //lose an opportunity to signal if a fiber is cancelled while committing
                  if (retryImmediately) commit(txn)
                  else log.signal >> F.pure(a)
              } yield r
            case TFailure(e) =>
              log
                .withLock(F.ifM(log.isDirty)(F.pure(true), F.pure(false)))
                .flatMap { retryImmediately =>
                  if (retryImmediately) commit(txn) else F.raiseError[A](e)
                }
            //TODO make retry blocking safely cancellable
            case TRetry =>
              //TODO we could probably split commit so we don't reallocate a signal every time
              log
                .withLock(
                  F.ifM(log.isDirty)(
                    // F.delay(println(s"${log.values.map(e => e.tvar.value -> e.initial -> e.current)} is dirty")) >> F.pure(true),
                    F.delay(println("dirty retry log")) >> log.debug >> F.pure(true),
                    // F.delay(println(s"${log.values.map(e => e.tvar.value -> e.initial -> e.current)} is clean")) >> F.delay(
                    //   println("registering retry")
                    // ) >> log.registerRetry(signal).as(false)
                    F.delay(println("registering retry")) >> log.debug >> log.registerRetry(signal).as(false)
                  )
                )
                .flatMap { retryImmediately =>
                  //TODO remove signal from tvars when we wake
                  if (retryImmediately) commit(txn) else signal.get >> commit(txn)
                }
          }
        } yield r

    }

}
