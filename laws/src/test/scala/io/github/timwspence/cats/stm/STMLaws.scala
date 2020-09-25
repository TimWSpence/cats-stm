package io.github.timwspence.cats.stm

import cats.kernel.laws._
import cats.implicits._

import io.github.timwspence.cats.stm._

trait STMLaws {

  def setThenGet[A](a: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.get) <-> (tvar.set(a) >> STM.pure(a))

  def setThenSet[A](a: A, b: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.set(b)) <-> tvar.set(a)

  def retryOrElse[A](stm: STM[A]) =
    (STM.retry[A] orElse stm) <-> stm

  def abortOrElse[A](error: Throwable, stm: STM[A]) =
    (STM.abort[A](error) orElse stm) <-> STM.abort[A](error)

  def retryOrElseRetry[A] =
    (STM.retry[A] orElse STM.retry[A]) <-> STM.retry[A]

}
