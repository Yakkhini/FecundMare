package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.dpic.{MemRead, MemWrite}
import taohe.util.AXI4LiteBundle

object SRAMState extends ChiselEnum {
  /*
   * SRAM State
   *
   * There is two FSM in SRAM represent for
   * read transaction & write transaction.
   *
   * Currently we believe thar aw channel and
   * w channel is synchronous. So the ready
   * & valid signal in two channel would fire
   * in same cycle.
   *
   */
  val sIdle, sWork, sSend = Value
}

class SRAM extends Module {
  val io = IO(Flipped(new AXI4LiteBundle))

  val memRead = Module(new MemRead())
  val memWrite = Module(new MemWrite())

  val readState = RegInit(SRAMState.sIdle)
  val writeState = RegInit(SRAMState.sIdle)

  val arAddr = RegInit(0.U(32.W))
  val awAddr = RegInit(0.U(32.W))
  val wData = RegInit(0.U(32.W))
  val wStrb = RegInit(0.U(4.W))

  // Read FSM
  // State 1
  io.ar.ready := readState === SRAMState.sIdle
  arAddr := Mux(io.ar.fire, io.ar.bits.addr, arAddr)

  // State 2
  // Consider to skip Work state since transaction
  // should finish in two cycles.
  memRead.io.readAddr := Mux(io.ar.fire, io.ar.bits.addr, arAddr)
  memRead.io.readEnable := io.ar.fire || readState === SRAMState.sSend // Work State Skipped
  io.r.bits.data := memRead.io.readData

  // State 3
  io.r.valid := readState === SRAMState.sSend

  // Write FSM
  // State 1
  io.aw.ready := writeState === SRAMState.sIdle
  io.w.ready := writeState === SRAMState.sIdle
  awAddr := Mux(io.aw.fire, io.aw.bits.addr, awAddr)
  wData := Mux(io.w.fire, io.w.bits.data, wData)

  // State 2
  memWrite.io.writeAddr := Mux(io.aw.fire, io.aw.bits.addr, awAddr)
  memWrite.io.writeData := Mux(io.w.fire, io.w.bits.data, wData)

  // State 3
  io.b.valid := writeState === SRAMState.sSend
  io.b.bits.resp := writeState === SRAMState.sSend

  switch(readState) {
    is(SRAMState.sIdle) {
      when(io.ar.fire) {
        readState := SRAMState.sSend
      }
    }
    is(SRAMState.sWork) {
      readState := SRAMState.sSend
    }
    is(SRAMState.sSend) {
      when(io.r.fire) {
        readState := SRAMState.sIdle
      }
    }
  }

  switch(writeState) {
    is(SRAMState.sIdle) {
      when(io.aw.fire && io.w.fire) {
        writeState := SRAMState.sSend
      }
    }
    is(SRAMState.sWork) {
      writeState := SRAMState.sSend
    }
    is(SRAMState.sSend) {
      when(io.b.fire) {
        writeState := SRAMState.sIdle
      }
    }
  }

}
