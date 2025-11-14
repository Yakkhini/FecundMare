package taohe.util

import chisel3._
import chisel3.experimental.AffectsChiselPrefix

class PerformanceCounter(width: Int) extends AffectsChiselPrefix {
  val value = RegInit(0.U(width.W))
  dontTouch(value)
  def inc(): Unit = {
    value := value + 1.U
  }
}

object PerformanceCounter {
  def apply(enable: Bool, width: Int = 32): UInt = {
    val counter = new PerformanceCounter(width)
    when(enable) {
      counter.inc()
    }
    counter.value
  }
}
