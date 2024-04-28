package taohe

import chisel3._
import chisel3.util.MuxLookup

import taohe.util.EXUBundle
import taohe.util.enum._

import chisel3.util.Fill

class EXU extends Module {
  val io = IO(new EXUBundle)

  io.withMemory.address := io.withRegisterFile.readData1 + io.fromIDU.imm
  io.withMemory.lenth := io.fromIDU.memoryLenth
  io.withMemory.writeData := io.withRegisterFile.readData2
  io.withMemory.writeEnable := (io.fromIDU.instructionType === InstType.S.asUInt)
  io.withMemory.valid := io.fromIDU.memoryValid

  val data1 = MuxLookup(io.fromIDU.data1Type, 0.U(32.W))(
    Seq(
      Data1Type.RS1.asUInt -> io.withRegisterFile.readData1,
      Data1Type.PC.asUInt -> io.currentPC
    )
  )

  val data2 = MuxLookup(io.fromIDU.data2Type, 0.U(32.W))(
    Seq(
      Data2Type.RS2.asUInt -> io.withRegisterFile.readData2,
      Data2Type.IMM.asUInt -> io.fromIDU.imm
    )
  )

  val memoryReadData = MuxLookup(io.fromIDU.memoryLenth, 0.U(32.W))(
    Seq(
      MemLen.B.asUInt -> Fill(
        24,
        io.withMemory.readData(7) & ~io.fromIDU.unsigned
      ) ## io.withMemory.readData(7, 0),
      MemLen.H.asUInt -> Fill(
        16,
        io.withMemory.readData(15) & ~io.fromIDU.unsigned
      ) ## io.withMemory.readData(15, 0),
      MemLen.W.asUInt -> io.withMemory.readData
    )
  )

  val result = MuxLookup(io.fromIDU.aluOp, 0.U(32.W))(
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

  val compareCheck = MuxLookup(io.fromIDU.compareOp, false.B)(
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
    io.currentPC + io.fromIDU.imm,
    io.currentPC + 4.U
  )

  io.nextPC := MuxLookup(
    io.fromIDU.nextPCType,
    0.U(32.W)
  )(
    Seq(
      NextPCDataType.RESULT.asUInt -> (result & (~1.U(32.W))),
      NextPCDataType.BRANCH.asUInt -> branchTarget,
      NextPCDataType.CSRDATA.asUInt -> io.csrData,
      NextPCDataType.NORMAL.asUInt -> (io.currentPC + 4.U)
    )
  )

  io.withRegisterFile.writeData := MuxLookup(
    io.fromIDU.registerWriteType,
    0.U(32.W)
  )(
    Seq(
      RegWriteDataType.RESULT.asUInt -> result,
      RegWriteDataType.NEXTPC.asUInt -> (io.currentPC + 4.U),
      RegWriteDataType.MEMREAD.asUInt -> memoryReadData,
      RegWriteDataType.CSRDATA.asUInt -> io.csrData
    )
  )

  io.withRegisterFile.writeEnable := Mux(
    (io.fromIDU.instructionType === InstType.S.asUInt) || (io.fromIDU.instructionType === InstType.B.asUInt),
    false.B,
    true.B
  )

  val powerManager = Module(new PowerManager())
  powerManager.io.reset := reset
  powerManager.io.breakSignal := io.fromIDU.break
  powerManager.io.code := data1
}
