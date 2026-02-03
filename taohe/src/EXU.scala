package taohe

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.{switch, is}

import taohe.util.EXUBundle
import taohe.util.enum._
import taohe.util.PerformanceCounter

import chisel3.util.Fill

object EXUState extends ChiselEnum {
  val sIdle, sLS, sWB = Value
}

class EXU extends Module {
  val io = IO(new EXUBundle)
  val exuState = RegInit(EXUState.sIdle)

  // State 1
  io.fromIDU.ready := exuState === EXUState.sIdle
  val iduSkidBuffer = RegInit(0.U.asTypeOf(io.fromIDU.bits))
  iduSkidBuffer := Mux(io.fromIDU.fire, io.fromIDU.bits, iduSkidBuffer)

  // State 2
  val clint = Module(new CLINT())
  clint.io.mmioAddress := iduSkidBuffer.registerReadData1 + iduSkidBuffer.imm
  clint.io.readEnable := iduSkidBuffer.lsuReadEnable

  val skipLSState =
    exuState === EXUState.sLS && (!iduSkidBuffer.lsuReadEnable || clint.io.clintChosen) && !iduSkidBuffer.lsuWriteEnable
  io.toLSU.valid := exuState === EXUState.sLS && !skipLSState
  io.fromLSU.ready := exuState === EXUState.sLS

  val difftestSkip = iduSkidBuffer.lsuReadEnable && clint.io.clintChosen
  dontTouch(difftestSkip)

  // State 3
  io.toRegisterFile.valid := exuState === EXUState.sWB || skipLSState
  io.toIFU.valid := exuState === EXUState.sWB || skipLSState
  io.toCSR.valid := exuState === EXUState.sWB || skipLSState

  io.fromCSR.ready := true.B

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
  val arithmeticDoneCounter = PerformanceCounter(
    io.toRegisterFile.valid &&
      io.toRegisterFile.bits.writeEnable &&
      iduSkidBuffer.registerWriteType === RegWriteDataType.RESULT.asUInt,
    32
  )

  switch(exuState) {
    is(EXUState.sIdle) {
      when(io.fromIDU.fire) {
        exuState := EXUState.sLS
      }
    }
    is(EXUState.sLS) {
      when(io.fromLSU.fire || io.toIFU.fire) {
        exuState := Mux(io.toIFU.fire, EXUState.sIdle, EXUState.sWB)
      }
    }
    is(EXUState.sWB) {
      when(io.toRegisterFile.fire && io.toIFU.fire) {
        exuState := EXUState.sIdle
      }
    }
  }

  val haltUnit = Module(new HaltUnit())
  haltUnit.io.reset := reset
  haltUnit.io.breakSignal := iduSkidBuffer.break
  haltUnit.io.code := data1
}
