package org.timwspence.cats.stm

import java.util.concurrent.atomic.AtomicReference

import org.timwspence.cats.stm.STM.internal._

class TVar[A] private[stm] (
  private val id: Long,
  @volatile private[stm] var value: A,
  private[stm] val pending: AtomicReference[Map[Long, Pending]]
) {

  def get: STM[A] = STM { log =>
    val entry = getOrInsert(log)
    TSuccess(entry.unsafeGet[A])
  }

  def set(a: A): STM[Unit] = STM { log =>
    val entry = getOrInsert(log)
    TSuccess(entry.unsafeSet(a))
  }

  def modify(f: A => A): STM[Unit] = STM { log =>
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

  def make[A](value: A): STM[TVar[A]] = STM { _ =>
    val id = IdGen.incrementAndGet()
    TSuccess(new TVar(id, value, new AtomicReference(Map())))
  }

}
