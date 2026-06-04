/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.MuxLookup

import fecundmare.util.enum._

class ALUBundle(implicit config: FMConfig) extends FMBundle {
  val operand1 = Input(UInt(XLEN.W))
  val operand2 = Input(UInt(XLEN.W))
  val operation = Input(UInt(ALUOpType.getWidth.W))

  val result = Output(UInt(XLEN.W))
}

class ArithmeticLogicUnit(implicit config: FMConfig) extends FMModule {
  val io = IO(new ALUBundle)

  io.result := MuxLookup(io.operation, 0.U(XLEN.W))(
    Seq(
      ALUOpType.ADD.asUInt -> (io.operand1 + io.operand2),
      ALUOpType.SUB.asUInt -> (io.operand1 - io.operand2),
      ALUOpType.AND.asUInt -> (io.operand1 & io.operand2),
      ALUOpType.OR.asUInt -> (io.operand1 | io.operand2),
      ALUOpType.XOR.asUInt -> (io.operand1 ^ io.operand2),
      ALUOpType.SLL.asUInt -> (io.operand1 << io.operand2(4, 0)),
      ALUOpType.SRL.asUInt -> (io.operand1 >> io.operand2(4, 0)),
      ALUOpType.SRA.asUInt -> (io.operand1.asSInt >> io.operand2(4, 0)).asUInt,
      ALUOpType.SLT.asUInt -> (io.operand1.asSInt < io.operand2.asSInt).asUInt,
      ALUOpType.SLTU.asUInt -> (io.operand1 < io.operand2).asUInt
    )
  )
}
