/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.MuxLookup

import fecundmare.util.enum._

class BJUBundle(implicit config: FMConfig) extends FMBundle {
  val operand1 = Input(UInt(XLEN.W))
  val operand2 = Input(UInt(XLEN.W))
  val operation = Input(UInt(BJUOpType.getWidth.W))

  val currentPC = Input(UInt(XLEN.W))
  val immNumber = Input(UInt(XLEN.W))

  val target = Output(UInt(XLEN.W))
}

class BranchJumpUnit(implicit config: FMConfig) extends FMModule {
  val io = IO(new BJUBundle)

  val compareResult = MuxLookup(io.operation, false.B)(
    Seq(
      BJUOpType.EQ.asUInt -> (io.operand1 === io.operand2),
      BJUOpType.NE.asUInt -> (io.operand1 =/= io.operand2),
      BJUOpType.LT.asUInt -> (io.operand1.asSInt < io.operand2.asSInt),
      BJUOpType.GE.asUInt -> (io.operand1.asSInt >= io.operand2.asSInt),
      BJUOpType.LTU.asUInt -> (io.operand1 < io.operand2),
      BJUOpType.GEU.asUInt -> (io.operand1 >= io.operand2),
      BJUOpType.JUMP.asUInt -> true.B
    )
  )

  val takenAddress = Mux(
    io.operation === BJUOpType.JUMP.asUInt,
    io.operand1 + io.operand2,
    io.currentPC + io.immNumber
  )
  val notTakenAddress = io.currentPC + 4.U

  io.target := Mux(compareResult, takenAddress, notTakenAddress)
}
