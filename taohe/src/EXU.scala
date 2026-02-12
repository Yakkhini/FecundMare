package taohe

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.{switch, is}

import taohe.util.EXUBundle
import taohe.util.enum._
import taohe.util.PreSiliconPerformanceCounter

import chisel3.util.Fill

object EXUState extends ChiselEnum {
  val sInit, sWork, sLS = Value
}

class EXU extends Module {
  val io = IO(new EXUBundle)
  val exuState = RegInit(EXUState.sInit)

  // State 1
  io.fromIDU.ready := exuState === EXUState.sInit || (exuState === EXUState.sWork && io.toIFU.ready)
  val iduSkidBuffer = RegInit(0.U.asTypeOf(io.fromIDU.bits))
  iduSkidBuffer := Mux(io.fromIDU.fire, io.fromIDU.bits, iduSkidBuffer)

  val clint = Module(new CLINT())
  clint.io.mmioAddress := iduSkidBuffer.registerReadData1 + iduSkidBuffer.imm
  clint.io.readEnable := iduSkidBuffer.lsuReadEnable

  val difftestSkip = iduSkidBuffer.lsuReadEnable && clint.io.clintChosen
  dontTouch(difftestSkip)

  val switchToLSU =
    exuState === EXUState.sWork && (io.fromIDU.bits.lsuReadEnable || io.fromIDU.bits.lsuWriteEnable)
  val lsDone = Wire(Bool())

  io.toIFU.valid := exuState === EXUState.sWork || lsDone
  io.toRegisterFile.valid := (exuState === EXUState.sWork && io.toIFU.fire) || lsDone
  io.toCSR.valid := (exuState === EXUState.sWork && io.toIFU.fire) || lsDone

  io.fromCSR.ready := true.B

  io.toIFU.bits.commit := io.fromIDU.fire

  // State 2
  io.toLSU.valid := exuState === EXUState.sLS && !clint.io.clintChosen
  io.fromLSU.ready := exuState === EXUState.sLS && !clint.io.clintChosen

  lsDone := exuState === EXUState.sLS && (io.fromLSU.fire || clint.io.clintChosen)

  // Inner Logic
  io.toRegisterFile.bits.writeAddr := iduSkidBuffer.registerWriteAddr

  io.toCSR.bits.address := iduSkidBuffer.csrAddress
  io.toCSR.bits.currentPC := iduSkidBuffer.currentPC
  io.toCSR.bits.operation := iduSkidBuffer.csrOperation
  io.toCSR.bits.rs1data := iduSkidBuffer.registerReadData1

  io.toLSU.bits.address := iduSkidBuffer.registerReadData1 + iduSkidBuffer.imm
  io.toLSU.bits.length := iduSkidBuffer.lsuLength
  io.toLSU.bits.writeData := iduSkidBuffer.registerReadData2
  io.toLSU.bits.writeEnable := iduSkidBuffer.lsuWriteEnable
  io.toLSU.bits.readEnable := iduSkidBuffer.lsuReadEnable && !clint.io.clintChosen

  val data1 = MuxLookup(iduSkidBuffer.data1Type, 0.U(32.W))(
    Seq(
      Data1Type.RS1.asUInt -> iduSkidBuffer.registerReadData1,
      Data1Type.PC.asUInt -> iduSkidBuffer.currentPC
    )
  )

  val data2 = MuxLookup(iduSkidBuffer.data2Type, 0.U(32.W))(
    Seq(
      Data2Type.RS2.asUInt -> iduSkidBuffer.registerReadData2,
      Data2Type.IMM.asUInt -> iduSkidBuffer.imm
    )
  )

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

  val result = MuxLookup(iduSkidBuffer.aluOp, 0.U(32.W))(
    Seq(
      ALUOpType.ADD.asUInt -> (data1 + data2),
      ALUOpType.SUB.asUInt -> (data1 - data2),
      ALUOpType.AND.asUInt -> (data1 & data2),
      ALUOpType.OR.asUInt -> (data1 | data2),
      ALUOpType.XOR.asUInt -> (data1 ^ data2),
      ALUOpType.SLL.asUInt -> (data1 << data2(4, 0)),
      ALUOpType.SRL.asUInt -> (data1 >> data2(4, 0)),
      ALUOpType.SRA.asUInt -> (data1.asSInt >> data2(4, 0)).asUInt,
      ALUOpType.SLT.asUInt -> (data1.asSInt < data2.asSInt).asUInt,
      ALUOpType.SLTU.asUInt -> (data1 < data2).asUInt
    )
  )

  val compareCheck = MuxLookup(iduSkidBuffer.compareOp, false.B)(
    Seq(
      CompareOpType.EQ.asUInt -> (data1 === data2),
      CompareOpType.NE.asUInt -> (data1 =/= data2),
      CompareOpType.LT.asUInt -> (data1.asSInt < data2.asSInt),
      CompareOpType.GE.asUInt -> (data1.asSInt >= data2.asSInt),
      CompareOpType.LTU.asUInt -> (data1 < data2),
      CompareOpType.GEU.asUInt -> (data1 >= data2)
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
    exuState === EXUState.sLS && lsDone,
    32
  )
  PreSiliconPerformanceCounter(
    "memoryStallCycleCounter",
    exuState === EXUState.sLS,
    32
  )

  switch(exuState) {
    is(EXUState.sInit) {
      when(io.fromIDU.fire) {
        exuState := EXUState.sWork
      }
    }
    is(EXUState.sWork) {
      when(io.fromIDU.fire && switchToLSU) {
        exuState := EXUState.sLS
      }
    }
    is(EXUState.sLS) {
      when(lsDone) {
        exuState := EXUState.sWork
      }
    }
  }

  val haltUnit = Module(new HaltUnit())
  haltUnit.io.reset := reset
  haltUnit.io.breakSignal := iduSkidBuffer.break
  haltUnit.io.code := data1
}
