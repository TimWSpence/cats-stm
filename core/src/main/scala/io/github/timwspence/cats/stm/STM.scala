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

import scala.concurrent.duration._
import scala.util.Random

import cats.effect.std.Semaphore
import cats.effect.{Async, Deferred, Ref}
import cats.implicits._

trait STM[F[_]] extends STMLike[F] with TMVarLike[F] with TQueueLike[F] with TSemaphoreLike[F] {}

object STM {
  def apply[F[_]]()(implicit F: Async[F]): F[STM[F]] =
    for {
      idGen  <- Ref.of[F, Long](0)
      global <- Semaphore[F](1) //TODO remove this and just lock each tvar
    } yield new STM[F] {
      import Internals._

      def commit[A](txn: Txn[A]): F[A] =
        for {
          signal <- Deferred[F, Unit]
          //TODO why does this need to be a critical section?
          p <- global.permit.use(_ => eval(idGen, txn))
          (res, log) = p
          r <- res match {
            //Double-checked dirtyness for performance
            case TSuccess(a) =>
              if (log.isDirty) commit(txn)
              else
                for {
                  committed <- global.permit.use(_ =>
                    if (log.isDirty) F.pure(false)
                    else
                      log.commit.as(true)
                  )
                  r <-
                    if (committed) log.signal >> F.pure(a)
                    else commit(txn)
                } yield r
            case TFailure(e) => if (log.isDirty) commit(txn) else F.raiseError(e)
            //TODO make retry blocking safely cancellable
            case TRetry =>
              //TODO we could probably split commit so we don't reallocate a signal every time
              global.permit
                .use(_ =>
                  if (log.isDirty) F.pure(true)
                  else log.registerRetry(signal).as(false)
                )
                .flatMap { retryImmediately =>
                  //TODO remove signal from tvars when we wake
                  if (retryImmediately) commit(txn) else signal.get >> jitter >> commit(txn)
                }
          }
        } yield r

      //TODO do we need this to avoid thundering herds?
      private def jitter: F[Unit] =
        F.delay(Random.nextInt(10000)).flatMap { n =>
          F.sleep(n.micros)
        }
    }

}
