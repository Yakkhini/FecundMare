package taohe

import chisel3._

import taohe.util.enum._

import chisel3.util.{switch, is}
import chisel3.util.MuxLookup

import taohe.util.CSRBundle

class CSR extends Module {
  val io = IO(new CSRBundle)

  io.toEXU.valid := true.B
  io.fromEXU.ready := true.B

  // The initial value of the MSTATUS register is 0x00001800 to pass DiffTest
  val csrs = RegInit(
    VecInit(Seq("h00001800".U(32.W), 0.U(32.W), 0.U(32.W), 0.U(32.W)))
  )

  val (opType, valid) = CSROPType.safe(io.fromEXU.bits.operation)
  val index = MuxLookup(io.fromEXU.bits.address, 0.U)(
    Seq(
      CSREnum.MSTATUS.asUInt -> 0.U,
      CSREnum.MTVEC.asUInt -> 1.U,
      CSREnum.MEPC.asUInt -> 2.U,
      CSREnum.MCAUSE.asUInt -> 3.U
    )
  )

  switch(opType) {
    is(CSROPType.RW) {
      csrs(index) := Mux(io.fromEXU.fire, io.fromEXU.bits.rs1data, csrs(index))
    }
    is(CSROPType.RS) {
      csrs(index) := Mux(
        io.fromEXU.fire,
        csrs(index) | io.fromEXU.bits.rs1data,
        csrs(index)
      )
    }
    is(CSROPType.CALL) {
      csrs(2.U) := Mux(io.fromEXU.fire, io.fromEXU.bits.currentPC, csrs(2.U))
      csrs(3.U) := 11.U(32.W) // MCAUSE
    }
  }

  io.toEXU.bits.readData := MuxLookup(opType.asUInt, 0.U)(
    Seq(
      CSROPType.RW.asUInt -> csrs(index),
      CSROPType.RS.asUInt -> csrs(index),
      CSROPType.CALL.asUInt -> csrs(1.U),
      CSROPType.RET.asUInt -> csrs(2.U),
      CSROPType.NONE.asUInt -> 0.U
    )
  )

}
