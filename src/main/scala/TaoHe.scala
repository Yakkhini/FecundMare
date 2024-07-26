package taohe

import chisel3._
import circt.stage.ChiselStage

import taohe.idu.IDU

class TaoHe extends Module {
  val io = IO(new Bundle {})

  val registerFile = Module(new RegisterFile())
  val csr = Module(new CSR())
  val lsu = Module(new LSU())
  lsu.io.clock := clock
  lsu.io.reset := reset

  val ifu = Module(new IFU())
  val idu = Module(new IDU())
  val exu = Module(new EXU())

  val sramArbiter = Module(new SRAMArbiter())
  ifu.io.axi4Lite <> sramArbiter.ifuIO
  lsu.io.axi4Lite <> sramArbiter.lsuIO

  ifu.io.toIDU <> idu.io.fromIFU
  idu.io.toEXU <> exu.io.fromIDU

  exu.io.fromRegisterFile <> registerFile.io.toEXU
  exu.io.fromCSR <> csr.io.toEXU
  exu.io.fromLSU <> lsu.io.toEXU

  exu.io.toRegisterFile <> registerFile.io.fromEXU
  exu.io.toCSR <> csr.io.fromEXU
  exu.io.toLSU <> lsu.io.fromEXU

  exu.io.toIFU <> ifu.io.fromEXU

  dontTouch(exu.io.toRegisterFile.bits.writeAddr)
  dontTouch(exu.io.toRegisterFile.bits.writeData)
  dontTouch(exu.io.toRegisterFile.bits.writeEnable)
  dontTouch(exu.io.toLSU.bits.lenth)
  dontTouch(exu.io.toLSU.bits.address)
  dontTouch(exu.io.toLSU.bits.writeData)
  dontTouch(exu.io.toLSU.bits.writeEnable)

}

object Main extends App {
  println("Hello World, I will generate the Verilog file now!")
  ChiselStage.emitSystemVerilogFile(
    gen = new TaoHe(),
    args = Array("--target-dir", "out/verilog", "--split-verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )
}
