package taohe

import chisel3._
import chisel3.util.MuxLookup

import taohe.util.EXUBundle
import taohe.idu.{
  Data1Type,
  Data2Type,
  RegWriteDataType,
  ImmType,
  InstType,
  ALUOpType,
  CompareOpType
}

class EXU extends Module {
  val io = IO(new EXUBundle)

  io.withMemory.address := io.withRegisterFile.readData1 + io.fromIDU.imm
  io.withMemory.lenth := io.fromIDU.memoryLenth
  io.withMemory.writeData := io.withRegisterFile.readData2
  io.withMemory.writeEnable := (io.fromIDU.instructionType === InstType.S.asUInt)

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
  val pcJumpTarget = Wire(UInt(32.W))

  branchTarget := Mux(
    compareCheck,
    io.currentPC + io.fromIDU.imm,
    io.currentPC + 4.U
  )

  pcJumpTarget := Mux(
    (io.fromIDU.instructionType === InstType.B.asUInt),
    branchTarget,
    result & (~1.U(32.W))
  )

  io.nextPC := Mux(io.fromIDU.jump, pcJumpTarget, io.currentPC + 4.U)
  io.withRegisterFile.writeData := MuxLookup(
    io.fromIDU.registerWriteType,
    0.U(32.W)
  )(
    Seq(
      RegWriteDataType.RESULT.asUInt -> result,
      RegWriteDataType.NEXTPC.asUInt -> (io.currentPC + 4.U),
      RegWriteDataType.MEMREAD.asUInt -> io.withMemory.readData
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
