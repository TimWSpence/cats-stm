package io.github.timwspence.cats.stm

/**
  * Convenience definition of a semaphore in the `STM` monad.
  *
  * Analogous to `cats.effect.concurrent.Semaphore`.
  */
class TSemaphore private[stm] (private val tvar: TVar[Long]) {

  /**
    * Get the number of permits currently available.
    */
  def available: STM[Long] = tvar.get

  /**
    * Acquire a permit. Retries if no permits are
    * available.
    */
  def acquire: STM[Unit] = tvar.get.flatMap {
    case 0 => STM.retry
    case _ => tvar.modify(_ - 1)
  }

  /**
    * Release a currently held permit.
    */
  def release: STM[Unit] = tvar.modify(_ + 1)
}

object TSemaphore {

  /**
    * Create a new `TSem` with `permits` available permits.
    */
  def make(permits: Long): STM[TSemaphore] = TVar.of(permits).map(tvar => new TSemaphore(tvar))

}
