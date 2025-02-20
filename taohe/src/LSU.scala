package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.LSUBundle
import chisel3.util.MuxLookup
import taohe.util.enum.MemSize

object LSUState extends ChiselEnum {
  /*
   * LSU State
   *
   * 1. Idle State
   * 2. Request State
   * 3. Wait State
   * 4. Send State
   *
   */
  val sIdle, sRequest, sWait, sSend = Value
}

class LSU extends Module {
  val io = IO(new LSUBundle)

  // Maybe there is two FSM in the future,
  // just like SRAM module.
  val lsuState = RegInit(LSUState.sIdle)

  // Buffer Register
  //
  // Notice: `writeData` and `readData` looks
  // similar, but they are different. EXU takes
  // master of `writeData`, and LSU takes master
  // of `readData`.
  val address = RegInit(0.U(32.W))
  val length = RegInit(0.U(32.W))
  val writeData = RegInit(0.U(32.W))
  val readData = RegInit(0.U(32.W))
  val writeEnable = RegInit(false.B)
  val readEnable = RegInit(false.B)

  // State 1
  io.fromEXU.ready := lsuState === LSUState.sIdle
  address := Mux(io.fromEXU.fire, io.fromEXU.bits.address, address)
  length := Mux(io.fromEXU.fire, io.fromEXU.bits.length, length)
  writeData := Mux(io.fromEXU.fire, io.fromEXU.bits.writeData, writeData)
  writeEnable := Mux(io.fromEXU.fire, io.fromEXU.bits.writeEnable, writeEnable)
  readEnable := Mux(io.fromEXU.fire, io.fromEXU.bits.readEnable, readEnable)

  // State 2
  val currentReadEnable =
    Mux(io.fromEXU.fire, io.fromEXU.bits.readEnable, readEnable)
  val currentWriteEnable =
    Mux(io.fromEXU.fire, io.fromEXU.bits.writeEnable, writeEnable)
  val currentAddress = Mux(
    io.fromEXU.fire,
    io.fromEXU.bits.address,
    address
  )
  io.axi4.ar.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentReadEnable
  io.axi4.aw.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentWriteEnable
  io.axi4.w.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentWriteEnable
  io.axi4.w.bits.last := io.axi4.w.valid
  io.axi4.ar.bits.size := io.fromEXU.bits.length
  io.axi4.ar.bits.addr := currentAddress
  io.axi4.aw.bits.size := io.fromEXU.bits.length
  io.axi4.aw.bits.addr := currentAddress
  // It should align with the bus width in AXI4 transaction
  io.axi4.w.bits.strb := MuxLookup(io.fromEXU.bits.length, "b1111".U)(
    Seq(
      MemSize.B.asUInt -> "b0001".U,
      MemSize.H.asUInt -> "b0011".U,
      MemSize.W.asUInt -> "b1111".U
    )
  ) << currentAddress(1, 0)
  io.axi4.w.bits.data := Mux(
    io.fromEXU.fire,
    io.fromEXU.bits.writeData,
    writeData
  ) << (currentAddress(1, 0) << 3)

  // Default value
  io.axi4.aw.bits.burst := 0.U
  io.axi4.aw.bits.id := 0.U
  io.axi4.aw.bits.len := 0.U
  io.axi4.ar.bits.id := 0.U
  io.axi4.ar.bits.len := 0.U
  io.axi4.ar.bits.burst := 0.U

  // State 3
  io.axi4.r.ready := lsuState === LSUState.sWait || lsuState === LSUState.sSend // Skip the Wait state
  readData := Mux(io.axi4.r.fire, io.axi4.r.bits.data, readData)
  io.axi4.b.ready := lsuState === LSUState.sWait

  // State 4
  //
  // It should align with the bus width in AXI4 transaction
  io.toEXU.valid := lsuState === LSUState.sSend || (lsuState === LSUState.sIdle && !currentWriteEnable && !currentReadEnable) || (lsuState === LSUState.sWait && io.axi4.r.fire)
  io.toEXU.bits.readData := Mux(
    io.axi4.r.fire,
    io.axi4.r.bits.data,
    readData
  ) >> (currentAddress(1, 0) << 3)

  switch(lsuState) {
    is(LSUState.sIdle) {
      when(io.fromEXU.fire) {
        lsuState := Mux(
          io.axi4.aw.fire || io.axi4.ar.fire,
          LSUState.sWait,
          LSUState.sRequest
        )
      }
    }
    is(LSUState.sRequest) {
      when(io.axi4.ar.fire || io.axi4.aw.fire) {
        lsuState := LSUState.sWait
      }
    }
    is(LSUState.sWait) {
      when(io.axi4.r.fire || io.axi4.b.fire) {
        lsuState := Mux(io.toEXU.fire, LSUState.sIdle, LSUState.sSend)
      }
    }
    is(LSUState.sSend) {
      when(io.toEXU.fire) {
        lsuState := LSUState.sIdle
      }
    }
  }

}
