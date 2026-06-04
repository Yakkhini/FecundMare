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
import fecundmare.FMConfig

abstract class FMModule(implicit val config: FMConfig) extends Module {
  final def XLEN = config.xlen
}

abstract class FMBundle(implicit val config: FMConfig) extends Bundle {
  final def XLEN = config.xlen
}

class FecundMare(config: FMConfig) extends Module {

  implicit val implicitConfig: FMConfig = config

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

  val instructionProcessing = Module(new InstructionProcessing())

  val axiArbiter = Module(new AXIArbiter())

  axiArbiter.io.instructionFetch <> iCache.io.axi4
  axiArbiter.io.loadStore <> lsu.io.axi4

  axiArbiter.io.out <> ioView.master

  iCache.io.fromIFU <> ifu.io.toICache
  iCache.io.toIFU <> ifu.io.fromICache

  ifu.io.toIDU <> idu.io.fromIFU

  idu.io.toEXU <> instructionProcessing.io.fromIDU

  idu.io.fromRegisterFile <> registerFile.io.toIDU
  idu.io.toRegisterFile <> registerFile.io.fromIDU

  instructionProcessing.io.fromCSR <> csr.io.toEXU
  instructionProcessing.io.fromLSU <> lsu.io.toEXU

  instructionProcessing.io.toRegisterFile <> registerFile.io.fromEXU
  instructionProcessing.io.toCSR <> csr.io.fromEXU
  instructionProcessing.io.toLSU <> lsu.io.fromEXU

  instructionProcessing.io.toIFU <> ifu.io.fromEXU

  dontTouch(instructionProcessing.io.toRegisterFile.bits.writeAddr)
  dontTouch(instructionProcessing.io.toRegisterFile.bits.writeData)
  dontTouch(instructionProcessing.io.toRegisterFile.bits.writeEnable)
  dontTouch(instructionProcessing.io.toLSU.bits.length)
  dontTouch(instructionProcessing.io.toLSU.bits.address)
  dontTouch(instructionProcessing.io.toLSU.bits.writeData)
  dontTouch(instructionProcessing.io.toLSU.bits.writeEnable)

  dontTouch(iCache.io.axi4.aw.ready)
  dontTouch(iCache.io.axi4.w.ready)
  dontTouch(iCache.io.axi4.b.valid)

}

object Main extends App {
  println("Hello World, I will generate the Verilog file now!")
  ChiselStage.emitSystemVerilogFile(
    gen = new FecundMare(FMConfig(xlen = 32, physicalVersion = false)),
    args = Array("--target-dir", "out/verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )

  ChiselStage.emitSystemVerilogFile(
    gen = new FecundMare(FMConfig(xlen = 32, physicalVersion = true)),
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
