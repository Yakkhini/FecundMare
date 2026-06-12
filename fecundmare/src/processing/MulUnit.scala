/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.{Cat, MuxLookup}
import chisel3.util.{switch, is}

import multiplier.SignedWallaceMultiplier

import fecundmare.util.enum._
import chisel3.util.Decoupled

class MULInputBundle(implicit config: FMConfig) extends FMBundle {
  val operand1 = UInt(XLEN.W)
  val operand2 = UInt(XLEN.W)
  val operation = UInt(MULOpType.getWidth.W)
}

class MULOutputBundle(implicit config: FMConfig) extends FMBundle {
  val result = UInt(XLEN.W)
}

class MULBundle(implicit config: FMConfig) extends FMBundle {
  val input = Flipped(Decoupled(new MULInputBundle))
  val output = Decoupled(new MULOutputBundle)
}

object MulUnitState extends ChiselEnum {
  val sIdle, sBusy = Value
}

class MulUnit(implicit config: FMConfig) extends FMModule {
  val io = IO(new MULBundle)
  val wallace = Module(
    new SignedWallaceMultiplier(XLEN + 1, XLEN + 1)(pipeAt = Seq(2, 5))
  )

  val state = RegInit(MulUnitState.sIdle)

  val cycleCounter = RegInit(0.U(2.W))
  cycleCounter := Mux(state === MulUnitState.sIdle, 0.U, cycleCounter + 1.U)

  val calculationDone = state === MulUnitState.sBusy && cycleCounter === 3.U

  io.input.ready := state === MulUnitState.sIdle

  val lhsSigned =
    io.input.bits.operation === MULOpType.MULH.asUInt ||
      io.input.bits.operation === MULOpType.MULHSU.asUInt
  val rhsSigned =
    io.input.bits.operation === MULOpType.MULH.asUInt

  wallace.a := Cat(
    lhsSigned && io.input.bits.operand1(XLEN - 1),
    io.input.bits.operand1
  ).asSInt
  wallace.b := Cat(
    rhsSigned && io.input.bits.operand2(XLEN - 1),
    io.input.bits.operand2
  ).asSInt

  io.output.valid := calculationDone

  val product = wallace.z.asUInt
  io.output.bits.result := MuxLookup(io.input.bits.operation, product(31, 0))(
    Seq(
      MULOpType.MUL.asUInt -> product(31, 0),
      MULOpType.MULH.asUInt -> product(63, 32),
      MULOpType.MULHSU.asUInt -> product(63, 32),
      MULOpType.MULHU.asUInt -> product(63, 32)
    )
  )

  switch(state) {
    is(MulUnitState.sIdle) {
      when(io.input.valid) {
        state := MulUnitState.sBusy
      }
    }
    is(MulUnitState.sBusy) {
      when(calculationDone) {
        state := MulUnitState.sIdle
      }
    }
  }

}
