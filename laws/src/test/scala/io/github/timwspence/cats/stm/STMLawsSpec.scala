package io.github.timwspence.cats.stm

import munit.{DisciplineSuite, FunSuite}

class STMLawsSpec extends FunSuite with DisciplineSuite with Instances {

  checkAll("stm", STMTests.apply.stm[Int])

}
