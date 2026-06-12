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
      dividendWidth = 32,
      dividerWidth = 32,
      n = 32
    )
  )

  val dividend = Reg(UInt(XLEN.W))
  val divider = Reg(UInt(XLEN.W))
  val operation = Reg(UInt(DIVOpType.getWidth.W))

  io.input.ready := state === DivUnitState.sIdle
  dividend := Mux(io.input.fire, io.input.bits.operand1, dividend)
  divider := Mux(io.input.fire, io.input.bits.operand2, divider)
  operation := Mux(io.input.fire, io.input.bits.operation, operation)

  val signed =
    operation === DIVOpType.DIV.asUInt || operation === DIVOpType.REM.asUInt

  val dividendZoroHeadNum = PriorityEncoder(Reverse(dividend))
  val dividerZoroHeadNum = PriorityEncoder(Reverse(divider))

  val needWidth =
    dividerZoroHeadNum - dividendZoroHeadNum + 2.U // SRT4 1 + radixLog2 - 1
  val guardWidth = needWidth(0)
  val counter = (needWidth + guardWidth) >> 1.U

  val dividendShift = dividendZoroHeadNum + 1.U - guardWidth
  val dividerShift = dividerZoroHeadNum

  val dividendNorm = dividend << dividendShift
  val dividerNorm = divider << dividerShift

  srt.input.valid := state === DivUnitState.sNorm
  srt.input.bits.dividend := dividendNorm
  srt.input.bits.divider := dividerNorm
  srt.input.bits.counter := counter

  val quitient = Reg(UInt(XLEN.W))
  val reminder = Reg(UInt(XLEN.W))

  quitient := Mux(srt.output.valid, srt.output.bits.quotient, quitient)
  reminder := Mux(
    srt.output.valid,
    srt.output.bits.reminder >> dividerShift,
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
