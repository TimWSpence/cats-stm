package io.github.timwspence.cats.stm

import java.util.concurrent.atomic.AtomicReference

import io.github.timwspence.cats.stm.STM.internal._

/**
  * Transactional variable - a mutable memory location
  * that can be read or written to via `STM` actions.
  *
  * Analagous to `cats.effect.concurrent.Ref`.
  */
final class TVar[A] private[stm] (
  private[stm] val id: Long,
  @volatile private[stm] var value: A,
  private[stm] val pending: AtomicReference[Map[TxId, Txn]]
) {

  /**
    * Get the current value as an
    * `STM` action.
    */
  def get: STM[A] =
    STM { log =>
      val entry = getOrInsert(log)
      TSuccess(entry.unsafeGet[A])
    }

  /**
    * Set the current value as an
    * `STM` action.
    */
  def set(a: A): STM[Unit] =
    STM { log =>
      val entry = getOrInsert(log)
      TSuccess(entry.unsafeSet(a))
    }

  /**
    * Modify the current value as an
    * `STM` action.
    */
  def modify(f: A => A): STM[Unit] =
    STM { log =>
      val entry   = getOrInsert(log)
      val updated = f(entry.unsafeGet[A])
      TSuccess(entry.unsafeSet(updated))
    }

  private def getOrInsert(log: TLog): TLogEntry =
    if (log.contains(id))
      log(id)
    else {
      val entry = TLogEntry(this, value)
      log += id -> entry
      entry
    }

}

object TVar {

  def of[A](value: A): STM[TVar[A]] =
    STM { _ =>
      val id = IdGen.incrementAndGet
      TSuccess(new TVar(id, value, new AtomicReference(Map())))
    }

}
