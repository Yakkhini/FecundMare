package fecundmare

import chisel3._
import chisel3.util.MuxLookup

class CLINT extends Module {
  val io = IO(new Bundle {
    val mmioAddress = Input(UInt(32.W))
    val readEnable = Input(Bool())
    val clintChosen = Output(Bool())
    val outputMTime = Output(UInt(32.W))
  })

  val mtime = RegInit(0.U(64.W))
  mtime := mtime + 1.U

  io.clintChosen := (io.mmioAddress === "h02000000".U || io.mmioAddress === "h02000004".U) && io.readEnable

  // CLINT 0x02000000(low) 0x02000004(high)
  io.outputMTime := MuxLookup(io.mmioAddress, 0.U(32.W))(
    Seq(
      "h02000000".U -> mtime(31, 0),
      "h02000004".U -> mtime(63, 32)
    )
  )

}
