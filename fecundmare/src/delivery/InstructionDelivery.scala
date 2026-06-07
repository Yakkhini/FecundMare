/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.Decoupled

import fecundmare.util._
import fecundmare.decode.InstDecodeUnit

class InstructionDeliveryBundle(implicit config: FMConfig) extends FMBundle {
  val fromProcessing = Flipped(Decoupled(new EXUToIFUBundle))
  val toProcessing = Decoupled(new IDUToEXUBundle)

  val fromRegisterFile = Flipped(Decoupled(new RegisterFileToIDUBundle))
  val toRegisterFile = Decoupled(new IDUToRegisterFileBundle)

  val axi4 = new AXI4Bundle
}

class InstructionDelivery(implicit config: FMConfig) extends FMModule {
  val io = IO(new InstructionDeliveryBundle)

  val iCache = Module(new ICache(4, 4))
  val ifu = Module(new IFU())
  val idu = Module(new InstDecodeUnit())

  iCache.io.fromIFU <> ifu.io.toICache
  iCache.io.toIFU <> ifu.io.fromICache

  ifu.io.toIDU <> idu.io.fromIFU
  ifu.io.fromEXU <> io.fromProcessing

  idu.io.toEXU <> io.toProcessing
  idu.io.fromRegisterFile <> io.fromRegisterFile
  idu.io.toRegisterFile <> io.toRegisterFile

  iCache.io.axi4 <> io.axi4

  dontTouch(iCache.io.axi4.aw.ready)
  dontTouch(iCache.io.axi4.w.ready)
  dontTouch(iCache.io.axi4.b.valid)
}
