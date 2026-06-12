/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.experimental.dataview._

import circt.stage.ChiselStage

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

  val instructionDelivery = Module(new InstructionDelivery())
  val lsu = Module(new LSU())
  val instructionProcessing = Module(new InstructionProcessing())

  val axiArbiter = Module(new AXIArbiter())

  axiArbiter.io.instructionFetch <> instructionDelivery.io.axi4
  axiArbiter.io.loadStore <> lsu.io.axi4

  axiArbiter.io.out <> ioView.master

  instructionProcessing.io.fromLSU <> lsu.io.toProcessing
  instructionProcessing.io.toLSU <> lsu.io.fromProcessing

  instructionDelivery.io.toProcessing <> instructionProcessing.io.fromDelivery
  instructionProcessing.io.toDelivery <> instructionDelivery.io.fromProcessing

  instructionDelivery.io.toRegisterFile <> instructionProcessing.io.fromDeliveryRegisterFile
  instructionProcessing.io.toDeliveryRegisterFile <> instructionDelivery.io.fromRegisterFile

  dontTouch(instructionProcessing.io.toLSU.bits.length)
  dontTouch(instructionProcessing.io.toLSU.bits.address)
  dontTouch(instructionProcessing.io.toLSU.bits.writeData)
  dontTouch(instructionProcessing.io.toLSU.bits.writeEnable)

}

object Main extends App {
  println("Hello World, I will generate the Verilog file now!")
  ChiselStage.emitSystemVerilogFile(
    gen = new FecundMare(FMConfig(xlen = 32, physicalVersion = false)),
    args = Array("--target-dir", "out/verilog"),
    firtoolOpts = Array(
      "-preserve-aggregate=1d-vec",
      "-disable-layers=Verification"
    )
  )

  ChiselStage.emitSystemVerilogFile(
    gen = new FecundMare(FMConfig(xlen = 32, physicalVersion = true)),
    args = Array("--target-dir", "out/sta"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowExpressionInliningInPorts,disallowPackedArrays",
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
