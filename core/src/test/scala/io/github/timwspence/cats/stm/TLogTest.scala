package io.github.timwspence.cats.stm

import cats.effect.IO

import munit.CatsEffectSuite

import io.github.timwspence.cats.stm.STM.internal._
import cats.effect.concurrent.Deferred

class TLogTest extends CatsEffectSuite {

  val inc: Int => Int = _ + 1

  test("get when not present") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      assertEquals(TLog.empty.get(tvar), 1)
    }
  }

  test("get when present") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      val tlog = TLog.empty
      tlog.get(tvar.asInstanceOf[TVar[Any]])
      tlog.modify(tvar, inc.asInstanceOf[Any => Any])
      assertEquals(tlog.get(tvar), 2)
    }
  }

  test("isDirty when empty") {
    val tlog = TLog.empty
    assertEquals(tlog.isDirty, false)
  }

  test("isDirty when non-empty") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      val tlog = TLog.empty
      tlog.get(tvar)
      assertEquals(tlog.isDirty, false)
    }
  }

  test("isDirty when non-empty and dirty") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      val tlog = TLog.empty
      tlog.modify(tvar, inc.asInstanceOf[Any => Any])
      tvar.value = 2
      assertEquals(tlog.isDirty, true)
    }
  }

  test("commit") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      val tlog = TLog.empty
      tlog.modify(tvar, inc.asInstanceOf[Any => Any])
      tlog.commit()
      assertEquals(tvar.value, 2)
    }
  }

  test("register retry") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      TVar.of[Any](2).atomically[IO].map { tvar2 =>
        val retryFiber = RetryFiber.make(tvar.get, 1L, Deferred.unsafe[IO, Either[Throwable, Any]])
        val tlog       = TLog.empty
        tlog.modify(tvar, inc.asInstanceOf[Any => Any])
        tlog.modify(tvar2, inc.asInstanceOf[Any => Any])
        tlog.registerRetry(3L, retryFiber)
        assertEquals(tvar.pending.get, Map(3L -> retryFiber))
        assertEquals(tvar2.pending.get, Map(3L -> retryFiber))
      }
    }
  }

  test("snapshot") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      TVar.of[Any](2).atomically[IO].map { tvar2 =>
        val tlog = TLog.empty
        tlog.modify(tvar, inc.asInstanceOf[Any => Any])
        val tlog2 = tlog.snapshot()
        tlog.modify(tvar, inc.asInstanceOf[Any => Any])
        assertEquals(tlog2.get(tvar), 2)
        assertEquals(tlog2.get(tvar2), 2)
      }
    }
  }

  test("delta") {
    TVar.of[Any](1).atomically[IO].map { tvar =>
      TVar.of[Any](2).atomically[IO].map { tvar2 =>
        val tlog = TLog.empty
        tlog.modify(tvar, inc.asInstanceOf[Any => Any])
        tlog.modify(tvar2, inc.asInstanceOf[Any => Any])
        val tlog2 = tlog.snapshot()
        tlog2.modify(tvar2, inc.asInstanceOf[Any => Any])
        val d = tlog2.delta(tlog)
        assertEquals(d.get(tvar), 2)
        assertEquals(d.get(tvar2), 3)
      }
    }
  }

}
