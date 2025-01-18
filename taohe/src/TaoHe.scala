package taohe

import chisel3._
import circt.stage.ChiselStage

import taohe.idu.IDU
import taohe.util.{YSYXSoCAXI4Bundle, AXI4Bundle}

class TaoHe extends Module {
  val io = IO(new Bundle {
    val interrupt = Input(Bool())
    val master = new YSYXSoCAXI4Bundle()
    val slave = Flipped(new YSYXSoCAXI4Bundle())
  })

  io <> DontCare

  val registerFile = Module(new RegisterFile())
  val csr = Module(new CSR())
  val lsu = Module(new LSU())
  val ifu = Module(new IFU())
  val idu = Module(new IDU())
  val exu = Module(new EXU())

  val axiArbiter = Module(new AXIArbiter())
  val xbar = Module(new CrossBar())

  val sram = Module(new SRAM())
  val uart = Module(new UART())
  val clint = Module(new CLINT())

  axiArbiter.io.ifu <> ifu.io.axi4Lite
  axiArbiter.io.lsu <> lsu.io.axi4Lite
  axiArbiter.io.out <> xbar.io.in

  xbar.io.out(0) <> sram.io
  xbar.io.out(1) <> uart.io
  xbar.io.out(2) <> clint.io

  ifu.io.toIDU <> idu.io.fromIFU
  idu.io.toEXU <> exu.io.fromIDU

  idu.io.fromRegisterFile <> registerFile.io.toIDU
  idu.io.toRegisterFile <> registerFile.io.fromIDU

  exu.io.fromCSR <> csr.io.toEXU
  exu.io.fromLSU <> lsu.io.toEXU

  exu.io.toRegisterFile <> registerFile.io.fromEXU
  exu.io.toCSR <> csr.io.fromEXU
  exu.io.toLSU <> lsu.io.fromEXU

  exu.io.toIFU <> ifu.io.fromEXU

  dontTouch(exu.io.toRegisterFile.bits.writeAddr)
  dontTouch(exu.io.toRegisterFile.bits.writeData)
  dontTouch(exu.io.toRegisterFile.bits.writeEnable)
  dontTouch(exu.io.toLSU.bits.length)
  dontTouch(exu.io.toLSU.bits.address)
  dontTouch(exu.io.toLSU.bits.writeData)
  dontTouch(exu.io.toLSU.bits.writeEnable)

  dontTouch(ifu.io.axi4Lite.aw.ready)
  dontTouch(ifu.io.axi4Lite.w.ready)
  dontTouch(ifu.io.axi4Lite.b.valid)

}

object Main extends App {
  println("Hello World, I will generate the Verilog file now!")
  ChiselStage.emitSystemVerilogFile(
    gen = new TaoHe(),
    args = Array("--target-dir", "out/verilog", "--split-verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )
}
