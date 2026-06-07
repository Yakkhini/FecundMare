/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.util.Decoupled
import chisel3.util.{switch, is}

import fecundmare.util._
import fecundmare.util.enum._
import fecundmare.util.PreSiliconPerformanceCounter

class InstructionProcessingBundle(implicit config: FMConfig) extends FMBundle {
  val fromDelivery = Flipped(Decoupled(new IDUToEXUBundle))
  val toDelivery = Decoupled(new EXUToIFUBundle)

  val fromDeliveryRegisterFile = Flipped(Decoupled(new IDUToRegisterFileBundle))
  val toDeliveryRegisterFile = Decoupled(new RegisterFileToIDUBundle)

  val fromLSU = Flipped(Decoupled(new LSUToEXUBundle))
  val toLSU = Decoupled(new EXUToLSUBundle)
}

object InstructionProcessingState extends ChiselEnum {
  val sInit, sWork, sLS = Value
}

class InstructionProcessing(implicit config: FMConfig) extends FMModule {
  val io = IO(new InstructionProcessingBundle)
  val exuState = RegInit(InstructionProcessingState.sInit)

  val registerFile = Module(new RegisterFile)
  val csr = Module(new CSR())

  registerFile.io.fromIDU <> io.fromDeliveryRegisterFile
  io.toDeliveryRegisterFile <> registerFile.io.toIDU

  dontTouch(registerFile.io.fromEXU.bits.writeAddr)
  dontTouch(registerFile.io.fromEXU.bits.writeData)
  dontTouch(registerFile.io.fromEXU.bits.writeEnable)

  // State 1
  io.fromDelivery.ready := exuState === InstructionProcessingState.sInit || (exuState === InstructionProcessingState.sWork && io.toDelivery.ready)
  val iduSkidBuffer = RegInit(0.U.asTypeOf(io.fromDelivery.bits))
  iduSkidBuffer := Mux(
    io.fromDelivery.fire,
    io.fromDelivery.bits,
    iduSkidBuffer
  )

  val clint = Module(new CLINT())
  clint.io.mmioAddress := iduSkidBuffer.data1 + iduSkidBuffer.imm
  clint.io.readEnable := iduSkidBuffer.lsuReadEnable

  val difftestSkip = iduSkidBuffer.lsuReadEnable && clint.io.clintChosen
  dontTouch(difftestSkip)

  val switchToLSU =
    exuState === InstructionProcessingState.sWork && (io.fromDelivery.bits.lsuReadEnable || io.fromDelivery.bits.lsuWriteEnable)
  val lsDone = Wire(Bool())

  io.toDelivery.valid := exuState === InstructionProcessingState.sWork || lsDone
  registerFile.io.fromEXU.valid := (exuState === InstructionProcessingState.sWork && io.toDelivery.fire) || lsDone
  csr.io.fromEXU.valid := (exuState === InstructionProcessingState.sWork && io.toDelivery.fire) || lsDone

  csr.io.toEXU.ready := true.B

  io.toDelivery.bits.commit := io.fromDelivery.fire

  // State 2
  io.toLSU.valid := exuState === InstructionProcessingState.sLS && !clint.io.clintChosen
  io.fromLSU.ready := exuState === InstructionProcessingState.sLS && !clint.io.clintChosen

  lsDone := exuState === InstructionProcessingState.sLS && (io.fromLSU.fire || clint.io.clintChosen)

  // Inner Logic
  registerFile.io.fromEXU.bits.writeAddr := iduSkidBuffer.registerWriteAddr

  csr.io.fromEXU.bits.address := iduSkidBuffer.csrAddress
  csr.io.fromEXU.bits.currentPC := iduSkidBuffer.currentPC
  csr.io.fromEXU.bits.operation := iduSkidBuffer.csrOperation
  csr.io.fromEXU.bits.rs1data := iduSkidBuffer.data1

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

  val bju = Module(new BranchJumpUnit())

  bju.io.operand1 := iduSkidBuffer.data1
  bju.io.operand2 := iduSkidBuffer.data2
  bju.io.operation := iduSkidBuffer.bjuOp

  bju.io.currentPC := iduSkidBuffer.currentPC
  bju.io.immNumber := iduSkidBuffer.imm

  val branchJumpTarget = bju.io.target

  io.toDelivery.bits.prevPC := iduSkidBuffer.currentPC
  io.toDelivery.bits.nextPC := MuxLookup(
    iduSkidBuffer.nextPCType,
    0.U(32.W)
  )(
    Seq(
      NextPCDataType.BRANCHJUMP.asUInt -> branchJumpTarget,
      NextPCDataType.CSRDATA.asUInt -> csr.io.toEXU.bits.readData,
      NextPCDataType.NORMAL.asUInt -> (iduSkidBuffer.currentPC + 4.U)
    )
  )

  registerFile.io.fromEXU.bits.writeData := MuxLookup(
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
      RegWriteDataType.CSRDATA.asUInt -> csr.io.toEXU.bits.readData
    )
  )

  registerFile.io.fromEXU.bits.writeEnable := iduSkidBuffer.registerWriteEnable

  // Performance Counter
  PreSiliconPerformanceCounter(
    "arithmeticDoneCounter",
    registerFile.io.fromEXU.valid &&
      registerFile.io.fromEXU.bits.writeEnable &&
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
      when(io.fromDelivery.fire) {
        exuState := InstructionProcessingState.sWork
      }
    }
    is(InstructionProcessingState.sWork) {
      when(io.fromDelivery.fire && switchToLSU) {
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
