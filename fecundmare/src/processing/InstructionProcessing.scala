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
  val fromDelivery = Flipped(Decoupled(new IDUToProcessingBundle))
  val toDelivery = Decoupled(new ProcessingToIFUBundle)

  val fromDeliveryRegisterFile = Flipped(Decoupled(new IDUToRegisterFileBundle))
  val toDeliveryRegisterFile = Decoupled(new RegisterFileToIDUBundle)

  val fromLSU = Flipped(Decoupled(new LSUToProcessingBundle))
  val toLSU = Decoupled(new ProcessingToLSUBundle)
}

object InstructionProcessingState extends ChiselEnum {
  val sInit, sWork, sWaitUnit = Value
}

class InstructionProcessing(implicit config: FMConfig) extends FMModule {
  val io = IO(new InstructionProcessingBundle)
  val exuState = RegInit(InstructionProcessingState.sInit)

  val registerFile = Module(new RegisterFile)
  val csr = Module(new CSR())

  registerFile.io.fromIDU <> io.fromDeliveryRegisterFile
  io.toDeliveryRegisterFile <> registerFile.io.toIDU

  dontTouch(registerFile.io.fromProcessing.bits.writeAddr)
  dontTouch(registerFile.io.fromProcessing.bits.writeData)
  dontTouch(registerFile.io.fromProcessing.bits.writeEnable)

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

  val blockByUnit =
    exuState === InstructionProcessingState.sWork && (
      io.fromDelivery.bits.funcType === FuncType.MEM.asUInt ||
        io.fromDelivery.bits.funcType === FuncType.MUL.asUInt ||
        io.fromDelivery.bits.funcType === FuncType.DIV.asUInt
    )
  val unitDone = Wire(Bool())

  io.toDelivery.valid := exuState === InstructionProcessingState.sWork || unitDone
  registerFile.io.fromProcessing.valid := (exuState === InstructionProcessingState.sWork && io.toDelivery.fire) || unitDone
  csr.io.fromProcessing.valid := ((exuState === InstructionProcessingState.sWork && io.toDelivery.fire) || unitDone) && iduSkidBuffer.funcType === FuncType.CSR.asUInt

  csr.io.toProcessing.ready := true.B

  io.toDelivery.bits.commit := io.fromDelivery.fire

  // State 2
  registerFile.io.fromProcessing.bits.writeAddr := iduSkidBuffer.registerWriteAddr

  csr.io.fromProcessing.bits.address := iduSkidBuffer.csrAddress
  csr.io.fromProcessing.bits.currentPC := iduSkidBuffer.currentPC
  csr.io.fromProcessing.bits.operation := iduSkidBuffer.csrOperation
  csr.io.fromProcessing.bits.rs1data := iduSkidBuffer.data1

  io.toLSU.valid := exuState === InstructionProcessingState.sWaitUnit && !clint.io.clintChosen && iduSkidBuffer.funcType === FuncType.MEM.asUInt
  io.fromLSU.ready := exuState === InstructionProcessingState.sWaitUnit && !clint.io.clintChosen && iduSkidBuffer.funcType === FuncType.MEM.asUInt

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
  alu.io.operation := iduSkidBuffer.funcOpType(ALUOpType.getWidth - 1, 0)

  val aluResult = alu.io.result

  val bju = Module(new BranchJumpUnit())

  bju.io.operand1 := iduSkidBuffer.data1
  bju.io.operand2 := iduSkidBuffer.data2
  bju.io.operation := iduSkidBuffer.funcOpType(BJUOpType.getWidth - 1, 0)

  bju.io.currentPC := iduSkidBuffer.currentPC
  bju.io.immNumber := iduSkidBuffer.imm

  val branchJumpTarget = bju.io.target

  val mul = Module(new MulUnit())

  mul.io.input.valid := exuState === InstructionProcessingState.sWaitUnit && iduSkidBuffer.funcType === FuncType.MUL.asUInt
  mul.io.output.ready := exuState === InstructionProcessingState.sWaitUnit && iduSkidBuffer.funcType === FuncType.MUL.asUInt

  mul.io.input.bits.operand1 := iduSkidBuffer.data1
  mul.io.input.bits.operand2 := iduSkidBuffer.data2
  mul.io.input.bits.operation := iduSkidBuffer.funcOpType(
    MULOpType.getWidth - 1,
    0
  )

  val mulResult = mul.io.output.bits.result

  val div = Module(new DivUnit())

  div.io.input.valid := exuState === InstructionProcessingState.sWaitUnit && iduSkidBuffer.funcType === FuncType.DIV.asUInt
  div.io.output.ready := exuState === InstructionProcessingState.sWaitUnit && iduSkidBuffer.funcType === FuncType.DIV.asUInt

  div.io.input.bits.operand1 := iduSkidBuffer.data1
  div.io.input.bits.operand2 := iduSkidBuffer.data2
  div.io.input.bits.operation := iduSkidBuffer.funcOpType(
    DIVOpType.getWidth - 1,
    0
  )

  val divResult = div.io.output.bits.result

  unitDone := exuState === InstructionProcessingState.sWaitUnit && (
    io.fromLSU.fire ||
      mul.io.output.fire ||
      div.io.output.fire ||
      clint.io.clintChosen
  )

  io.toDelivery.bits.prevPC := iduSkidBuffer.currentPC
  io.toDelivery.bits.nextPC := MuxLookup(
    iduSkidBuffer.nextPCType,
    0.U(32.W)
  )(
    Seq(
      NextPCDataType.BRANCHJUMP.asUInt -> branchJumpTarget,
      NextPCDataType.CSRDATA.asUInt -> csr.io.toProcessing.bits.readData,
      NextPCDataType.NORMAL.asUInt -> (iduSkidBuffer.currentPC + 4.U)
    )
  )

  registerFile.io.fromProcessing.bits.writeData := MuxLookup(
    iduSkidBuffer.funcType,
    0.U(32.W)
  )(
    Seq(
      FuncType.ALU.asUInt -> aluResult,
      FuncType.BJU.asUInt -> (iduSkidBuffer.currentPC + 4.U),
      FuncType.MUL.asUInt -> mulResult,
      FuncType.DIV.asUInt -> divResult,
      FuncType.MEM.asUInt -> Mux(
        clint.io.clintChosen,
        clint.io.outputMTime,
        lsuReadData
      ),
      FuncType.CSR.asUInt -> csr.io.toProcessing.bits.readData
    )
  )

  registerFile.io.fromProcessing.bits.writeEnable := iduSkidBuffer.registerWriteEnable

  // Performance Counter
  PreSiliconPerformanceCounter(
    "arithmeticDoneCounter",
    registerFile.io.fromProcessing.valid &&
      registerFile.io.fromProcessing.bits.writeEnable &&
      iduSkidBuffer.funcType === FuncType.ALU.asUInt,
    32
  )
  PreSiliconPerformanceCounter(
    "memoryDoneCounter",
    exuState === InstructionProcessingState.sWaitUnit && unitDone && iduSkidBuffer.funcType === FuncType.MEM.asUInt,
    32
  )
  PreSiliconPerformanceCounter(
    "memoryStallCycleCounter",
    exuState === InstructionProcessingState.sWaitUnit && iduSkidBuffer.funcType === FuncType.MEM.asUInt,
    32
  )

  switch(exuState) {
    is(InstructionProcessingState.sInit) {
      when(io.fromDelivery.fire) {
        exuState := InstructionProcessingState.sWork
      }
    }
    is(InstructionProcessingState.sWork) {
      when(io.fromDelivery.fire && blockByUnit) {
        exuState := InstructionProcessingState.sWaitUnit
      }
    }
    is(InstructionProcessingState.sWaitUnit) {
      when(unitDone) {
        exuState := InstructionProcessingState.sWork
      }
    }
  }

  val haltUnit = Module(new HaltUnit())
  haltUnit.io.reset := reset
  haltUnit.io.breakSignal := iduSkidBuffer.break
  haltUnit.io.code := iduSkidBuffer.data1
}
