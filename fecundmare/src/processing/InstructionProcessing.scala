/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.{switch, is}

import fecundmare.util.EXUBundle
import fecundmare.util.enum._
import fecundmare.util.PreSiliconPerformanceCounter

import chisel3.util.Fill

object InstructionProcessingState extends ChiselEnum {
  val sInit, sWork, sLS = Value
}

class InstructionProcessing(implicit config: FMConfig) extends FMModule {
  val io = IO(new EXUBundle)
  val exuState = RegInit(InstructionProcessingState.sInit)

  // State 1
  io.fromIDU.ready := exuState === InstructionProcessingState.sInit || (exuState === InstructionProcessingState.sWork && io.toIFU.ready)
  val iduSkidBuffer = RegInit(0.U.asTypeOf(io.fromIDU.bits))
  iduSkidBuffer := Mux(io.fromIDU.fire, io.fromIDU.bits, iduSkidBuffer)

  val clint = Module(new CLINT())
  clint.io.mmioAddress := iduSkidBuffer.data1 + iduSkidBuffer.imm
  clint.io.readEnable := iduSkidBuffer.lsuReadEnable

  val difftestSkip = iduSkidBuffer.lsuReadEnable && clint.io.clintChosen
  dontTouch(difftestSkip)

  val switchToLSU =
    exuState === InstructionProcessingState.sWork && (io.fromIDU.bits.lsuReadEnable || io.fromIDU.bits.lsuWriteEnable)
  val lsDone = Wire(Bool())

  io.toIFU.valid := exuState === InstructionProcessingState.sWork || lsDone
  io.toRegisterFile.valid := (exuState === InstructionProcessingState.sWork && io.toIFU.fire) || lsDone
  io.toCSR.valid := (exuState === InstructionProcessingState.sWork && io.toIFU.fire) || lsDone

  io.fromCSR.ready := true.B

  io.toIFU.bits.commit := io.fromIDU.fire

  // State 2
  io.toLSU.valid := exuState === InstructionProcessingState.sLS && !clint.io.clintChosen
  io.fromLSU.ready := exuState === InstructionProcessingState.sLS && !clint.io.clintChosen

  lsDone := exuState === InstructionProcessingState.sLS && (io.fromLSU.fire || clint.io.clintChosen)

  // Inner Logic
  io.toRegisterFile.bits.writeAddr := iduSkidBuffer.registerWriteAddr

  io.toCSR.bits.address := iduSkidBuffer.csrAddress
  io.toCSR.bits.currentPC := iduSkidBuffer.currentPC
  io.toCSR.bits.operation := iduSkidBuffer.csrOperation
  io.toCSR.bits.rs1data := iduSkidBuffer.data1

  io.toLSU.bits.address := iduSkidBuffer.data1 + iduSkidBuffer.imm
  io.toLSU.bits.length := iduSkidBuffer.lsuLength
  io.toLSU.bits.writeData := iduSkidBuffer.data2
  io.toLSU.bits.writeEnable := iduSkidBuffer.lsuWriteEnable
  io.toLSU.bits.readEnable := iduSkidBuffer.lsuReadEnable && !clint.io.clintChosen

  val lsuReadData = MuxLookup(iduSkidBuffer.lsuLength, 0.U(32.W))(
    Seq(
      MemSize.B.asUInt -> Fill(
        24,
        io.fromLSU.bits.readData(7) & ~iduSkidBuffer.unsigned
      ) ## io.fromLSU.bits.readData(7, 0),
      MemSize.H.asUInt -> Fill(
        16,
        io.fromLSU.bits.readData(15) & ~iduSkidBuffer.unsigned
      ) ## io.fromLSU.bits.readData(15, 0),
      MemSize.W.asUInt -> io.fromLSU.bits.readData
    )
  )

  val alu = Module(new ArithmeticLogicUnit())

  alu.io.operand1 := iduSkidBuffer.data1
  alu.io.operand2 := iduSkidBuffer.data2
  alu.io.operation := iduSkidBuffer.aluOp

  val result = alu.io.result

  val compareCheck = MuxLookup(iduSkidBuffer.compareOp, false.B)(
    Seq(
      CompareOpType.EQ.asUInt -> (iduSkidBuffer.data1 === iduSkidBuffer.data2),
      CompareOpType.NE.asUInt -> (iduSkidBuffer.data1 =/= iduSkidBuffer.data2),
      CompareOpType.LT.asUInt -> (iduSkidBuffer.data1.asSInt < iduSkidBuffer.data2.asSInt),
      CompareOpType.GE.asUInt -> (iduSkidBuffer.data1.asSInt >= iduSkidBuffer.data2.asSInt),
      CompareOpType.LTU.asUInt -> (iduSkidBuffer.data1 < iduSkidBuffer.data2),
      CompareOpType.GEU.asUInt -> (iduSkidBuffer.data1 >= iduSkidBuffer.data2)
    )
  )

  val branchTarget = Wire(UInt(32.W))

  branchTarget := Mux(
    compareCheck,
    iduSkidBuffer.currentPC + iduSkidBuffer.imm,
    iduSkidBuffer.currentPC + 4.U
  )

  io.toIFU.bits.prevPC := iduSkidBuffer.currentPC
  io.toIFU.bits.nextPC := MuxLookup(
    iduSkidBuffer.nextPCType,
    0.U(32.W)
  )(
    Seq(
      NextPCDataType.RESULT.asUInt -> (result & (~1.U(32.W))),
      NextPCDataType.BRANCH.asUInt -> branchTarget,
      NextPCDataType.CSRDATA.asUInt -> io.fromCSR.bits.readData,
      NextPCDataType.NORMAL.asUInt -> (iduSkidBuffer.currentPC + 4.U)
    )
  )

  io.toRegisterFile.bits.writeData := MuxLookup(
    iduSkidBuffer.registerWriteType,
    0.U(32.W)
  )(
    Seq(
      RegWriteDataType.RESULT.asUInt -> result,
      RegWriteDataType.NEXTPC.asUInt -> (iduSkidBuffer.currentPC + 4.U),
      RegWriteDataType.MEMREAD.asUInt -> Mux(
        clint.io.clintChosen,
        clint.io.outputMTime,
        lsuReadData
      ),
      RegWriteDataType.CSRDATA.asUInt -> io.fromCSR.bits.readData
    )
  )

  io.toRegisterFile.bits.writeEnable := Mux(
    (iduSkidBuffer.instructionType === InstType.S.asUInt) || (iduSkidBuffer.instructionType === InstType.B.asUInt),
    false.B,
    true.B
  )

  // Performance Counter
  PreSiliconPerformanceCounter(
    "arithmeticDoneCounter",
    io.toRegisterFile.valid &&
      io.toRegisterFile.bits.writeEnable &&
      iduSkidBuffer.registerWriteType === RegWriteDataType.RESULT.asUInt,
    32
  )
  PreSiliconPerformanceCounter(
    "memoryDoneCounter",
    exuState === InstructionProcessingState.sLS && lsDone,
    32
  )
  PreSiliconPerformanceCounter(
    "memoryStallCycleCounter",
    exuState === InstructionProcessingState.sLS,
    32
  )

  switch(exuState) {
    is(InstructionProcessingState.sInit) {
      when(io.fromIDU.fire) {
        exuState := InstructionProcessingState.sWork
      }
    }
    is(InstructionProcessingState.sWork) {
      when(io.fromIDU.fire && switchToLSU) {
        exuState := InstructionProcessingState.sLS
      }
    }
    is(InstructionProcessingState.sLS) {
      when(lsDone) {
        exuState := InstructionProcessingState.sWork
      }
    }
  }

  val haltUnit = Module(new HaltUnit())
  haltUnit.io.reset := reset
  haltUnit.io.breakSignal := iduSkidBuffer.break
  haltUnit.io.code := iduSkidBuffer.data1
}
