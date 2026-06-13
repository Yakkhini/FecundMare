/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.Decoupled
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.util.PriorityEncoder
import chisel3.util.Reverse
import chisel3.util.log2Ceil
import chisel3.util.{switch, is}

import division.srt.SRT

import fecundmare.util.enum._

class DIVInputBundle(implicit config: FMConfig) extends FMBundle {
  val operand1 = UInt(XLEN.W)
  val operand2 = UInt(XLEN.W)
  val operation = UInt(DIVOpType.getWidth.W)
}

class DIVOutputBundle(implicit config: FMConfig) extends FMBundle {
  val result = UInt(XLEN.W)
}

class DIVBundle(implicit config: FMConfig) extends FMBundle {
  val input = Flipped(Decoupled(new DIVInputBundle))
  val output = Decoupled(new DIVOutputBundle)
}

object DivUnitState extends ChiselEnum {
  val sIdle, sNorm, sRun, sDone = Value
}

class DivUnit(implicit config: FMConfig) extends FMModule {
  val io = IO(new DIVBundle)

  val state = RegInit(DivUnitState.sIdle)

  val srt = Module(
    new division.srt.SRT(
      dividendWidth = XLEN,
      dividerWidth = XLEN,
      n = XLEN
    )
  )

  val dividendAbs = Reg(UInt(XLEN.W))
  val dividerAbs = Reg(UInt(XLEN.W))
  val operation = Reg(UInt(DIVOpType.getWidth.W))

  val dividendNegative = Reg(Bool())
  val dividerNegative = Reg(Bool())

  val inputSigned =
    io.input.bits.operation === DIVOpType.DIV.asUInt || io.input.bits.operation === DIVOpType.REM.asUInt

  io.input.ready := state === DivUnitState.sIdle
  dividendAbs := Mux(
    io.input.fire,
    Mux(
      inputSigned,
      io.input.bits.operand1.asSInt.abs.asUInt,
      io.input.bits.operand1
    ),
    dividendAbs
  )
  dividerAbs := Mux(
    io.input.fire,
    Mux(
      inputSigned,
      io.input.bits.operand2.asSInt.abs.asUInt,
      io.input.bits.operand2
    ),
    dividerAbs
  )
  operation := Mux(io.input.fire, io.input.bits.operation, operation)

  dividendNegative := Mux(
    io.input.fire,
    inputSigned && io.input.bits.operand1(XLEN - 1),
    dividendNegative
  )
  dividerNegative := Mux(
    io.input.fire,
    inputSigned && io.input.bits.operand2(XLEN - 1),
    dividerNegative
  )

  val dividendZoroHeadNum = PriorityEncoder(Reverse(dividendAbs))
  val dividerZoroHeadNum = PriorityEncoder(Reverse(dividerAbs))

  val needWidth =
    dividerZoroHeadNum - dividendZoroHeadNum + 2.U // SRT4 1 + radixLog2 - 1
  val guardWidth = needWidth(0)
  val counter = (needWidth + guardWidth) >> 1.U

  val dividendShift = dividendZoroHeadNum + 1.U - guardWidth
  val dividerShift = dividerZoroHeadNum

  val dividendNorm = dividendAbs << dividendShift
  val dividerNorm = dividerAbs << dividerShift

  srt.input.valid := state === DivUnitState.sNorm
  srt.input.bits.dividend := dividendNorm
  srt.input.bits.divider := dividerNorm
  srt.input.bits.counter := counter

  val quitient = Reg(UInt(XLEN.W))
  val reminder = Reg(UInt(XLEN.W))

  val signedQuitient = Mux(
    dividendNegative ^ dividerNegative,
    ~srt.output.bits.quotient + 1.U,
    srt.output.bits.quotient
  )
  val signedReminder = Mux(
    dividendNegative,
    ~(srt.output.bits.reminder >> dividerShift) + 1.U,
    srt.output.bits.reminder >> dividerShift
  )

  val outputSigned =
    operation === DIVOpType.DIV.asUInt || operation === DIVOpType.REM.asUInt

  quitient := Mux(
    srt.output.valid,
    Mux(outputSigned, signedQuitient, srt.output.bits.quotient),
    quitient
  )
  reminder := Mux(
    srt.output.valid,
    Mux(outputSigned, signedReminder, srt.output.bits.reminder >> dividerShift),
    reminder
  )

  io.output.valid := state === DivUnitState.sDone
  io.output.bits.result := MuxLookup(operation, quitient)(
    Seq(
      DIVOpType.DIV.asUInt -> quitient,
      DIVOpType.DIVU.asUInt -> quitient,
      DIVOpType.REM.asUInt -> reminder,
      DIVOpType.REMU.asUInt -> reminder
    )
  )

  switch(state) {
    is(DivUnitState.sIdle) {
      when(io.input.fire) {
        state := DivUnitState.sNorm
      }
    }
    is(DivUnitState.sNorm) {
      when(srt.input.fire) {
        state := DivUnitState.sRun
      }
    }
    is(DivUnitState.sRun) {
      when(srt.output.valid) {
        state := DivUnitState.sDone
      }
    }
    is(DivUnitState.sDone) {
      when(io.output.fire) {
        state := DivUnitState.sIdle
      }
    }
  }
}
