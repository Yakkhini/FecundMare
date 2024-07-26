package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.LSUBundle

object LSUState extends ChiselEnum {
  /*
   * Currently in multi-cycle design, there is
   * no need to distinguish between read and write
   * transaction. Only one type of transaction is
   * allowed in one cycle.
   *
   * LSU State
   *
   * 1. Idle State
   * 2. Request State
   * 3. Wait State
   * 4. Send State
   *
   * Now our design is connenct with a SRAM module.
   * So the read transaction should finish in two
   * cycles, and write transaction should finish in
   * one cycle. Maybe few states have to be skipped.
   *
   * We skip the Wait state in two transaction and
   * the Send state in write transaction.
   */
  val sIdle, sRequest, sWait, sSend = Value
}

class LSU extends Module {
  val io = IO(new LSUBundle)

}
