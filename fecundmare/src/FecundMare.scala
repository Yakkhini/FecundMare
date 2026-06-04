/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.experimental.dataview._

import circt.stage.ChiselStage

import fecundmare.idu.IDU
import fecundmare.util.{YSYXSoCAXI4Bundle, AXI4Bundle}
import fecundmare.FecundMareConfig

abstract class FMModule(implicit val config: FecundMareConfig) extends Module {
  final def XLEN = config.xlen
}

class FecundMare(config: FecundMareConfig) extends Module {

  implicit val implicitConfig: FecundMareConfig = config

  override def localModulePrefix =
    if (config.physicalVersion) Some("fecundmare_") else None

  val io = IO(new Bundle {
    val interrupt = Input(Bool())
    val master = new YSYXSoCAXI4Bundle()
    val slave = Flipped(new YSYXSoCAXI4Bundle())
  })

  io.interrupt <> DontCare
  io.slave <> DontCare

  val ioView = new Bundle {
    val interrupt = Input(Bool())
    val master = io.master.viewAs[AXI4Bundle]
    val slave = io.slave.viewAs[AXI4Bundle]
  }

  val registerFile = Module(new RegisterFile)
  val csr = Module(new CSR())

  val iCache = Module(new ICache(4, 4))

  val lsu = Module(new LSU())
  val ifu = Module(new IFU())
  val idu = Module(new IDU())
  val exu = Module(new EXU())

  val axiArbiter = Module(new AXIArbiter())

  axiArbiter.io.instructionFetch <> iCache.io.axi4
  axiArbiter.io.loadStore <> lsu.io.axi4

  axiArbiter.io.out <> ioView.master

  iCache.io.fromIFU <> ifu.io.toICache
  iCache.io.toIFU <> ifu.io.fromICache

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

  dontTouch(iCache.io.axi4.aw.ready)
  dontTouch(iCache.io.axi4.w.ready)
  dontTouch(iCache.io.axi4.b.valid)

}

object Main extends App {
  println("Hello World, I will generate the Verilog file now!")
  ChiselStage.emitSystemVerilogFile(
    gen = new FecundMare(FecundMareConfig(xlen = 32, physicalVersion = false)),
    args = Array("--target-dir", "out/verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )

  ChiselStage.emitSystemVerilogFile(
    gen = new FecundMare(FecundMareConfig(xlen = 32, physicalVersion = true)),
    args = Array("--target-dir", "out/sta"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowExpressionInliningInPorts",
      "-disable-layers=Verification,PerformanceCounterLayer"
    )
  )

  ChiselStage.emitSystemVerilogFile(
    gen = new ICacheTest(),
    args = Array("--target-dir", "out/formal"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowExpressionInliningInPorts,disallowPackedArrays"
    )
  )

}
