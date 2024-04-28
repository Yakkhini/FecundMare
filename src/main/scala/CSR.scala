package taohe

import chisel3._

import taohe.util.enum._

import chisel3.util.{switch, is}
import chisel3.util.MuxLookup

class CSR extends Module {
  val io = IO(new Bundle {
    val csrOperation = Input(UInt(CSROPType.getWidth.W))
    val address = Input(UInt(12.W))
    val currentPC = Input(UInt(32.W))
    val rs1data = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
  })

  // The initial value of the MSTATUS register is 0x00001800 to pass DiffTest
  val csrs = RegInit(
    VecInit(Seq("h00001800".U(32.W), 0.U(32.W), 0.U(32.W), 0.U(32.W)))
  )

  val (opType, valid) = CSROPType.safe(io.csrOperation)
  val index = MuxLookup(io.address, 0.U)(
    Seq(
      CSREnum.MSTATUS.asUInt -> 0.U,
      CSREnum.MTVEC.asUInt -> 1.U,
      CSREnum.MEPC.asUInt -> 2.U,
      CSREnum.MCAUSE.asUInt -> 3.U
    )
  )

  switch(opType) {
    is(CSROPType.RW) {
      csrs(index) := io.rs1data
    }
    is(CSROPType.RS) {
      csrs(index) := csrs(index) | io.rs1data
    }
    is(CSROPType.CALL) {
      csrs(2.U) := io.currentPC // MEPC
      csrs(3.U) := 11.U(32.W) // MCAUSE
    }
  }

  io.readData := MuxLookup(opType.asUInt, 0.U)(
    Seq(
      CSROPType.RW.asUInt -> csrs(index),
      CSROPType.RS.asUInt -> csrs(index),
      CSROPType.CALL.asUInt -> csrs(1.U),
      CSROPType.RET.asUInt -> csrs(2.U),
      CSROPType.NONE.asUInt -> 0.U
    )
  )

}
