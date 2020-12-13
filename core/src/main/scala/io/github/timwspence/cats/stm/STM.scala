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

  def apply[F[_]](implicit S: STM[F]): S.type = S

  /*
   * Construct an STM runtime with a fairly arbitrarily chosen bound on the
   * number of concurrent transactions
   */
  def runtime[F[_]](implicit F: Async[F]): F[STM[F]] = runtime(4)

  /*
   * Constructs an STM runtime.
   *
   * @param n the number of transactions that we allow to evaluate concurrently.
   * This is configurable as the optimal choice depends on the profile of your
   * application. Transactions are evaluated optimistically, with retries in the
   * event that they depend on dirty reads.
   *
   * This means that if you have a relatively small number of TVars with a high
   * level of contention then you may waste a lot of CPU spinning retries.
   *
   * Conversely, if you have a large number of TVars and low contention then you
   * should be able to set the limit much higher and make use of all available
   * resources.
   */
  def runtime[F[_]](n: Long)(implicit F: Async[F]): F[STM[F]] =
    for {
      idGen       <- Ref.of[F, Long](0)
      rateLimiter <- Semaphore[F](n)
    } yield new STM[F] {

      def commit[A](txn: Txn[A]): F[A] =
        for {
          p <- rateLimiter.permit.use(_ => eval(idGen, txn))
          (res, log) = p
          r <- res match {
            case TSuccess(a) =>
              //Uncancelable so that we don't lose opportunities for retries
              //where a fiber commits and is immediately cancelled
              F.uncancelable(poll =>
                for {
                  retryImmediately <- poll(
                    log.withLock(
                      F.ifM(log.isDirty)(
                        F.pure(true),
                        log.commit.as(false)
                      )
                    )
                  )
                  r <-
                    if (retryImmediately) poll(commit(txn))
                    else log.signal >> F.pure(a)
                } yield r
              )
            case TFailure(e) =>
              log
                .withLock(F.ifM(log.isDirty)(F.pure(true), F.pure(false)))
                .flatMap { retryImmediately =>
                  if (retryImmediately) commit(txn) else F.raiseError[A](e)
                }
            case TRetry =>
              for {
                signal <- Deferred[F, Unit]
                retryImmediately <-
                  log
                    .withLock(
                      F.ifM(log.isDirty)(
                        F.pure(true),
                        log.registerRetry(signal).as(false)
                      )
                    )
                //TODO is it worth removing signals from tvars when we wake?
                res <- if (retryImmediately) commit(txn) else signal.get >> commit(txn)
              } yield res
          }
        } yield r

    }

}
