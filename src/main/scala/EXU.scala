package taohe

import chisel3._
import chisel3.util.MuxLookup

import taohe.util.EXUBundle
import taohe.util.enum._

import chisel3.util.Fill

class EXU extends Module {
  val io = IO(new EXUBundle)

  io.toRegisterFile.valid := false.B

  io.fromIDU.ready := false.B
  io.fromRegisterFile.ready := false.B
  io.fromMemory.ready := false.B

  dontTouch(io.fromMemory.valid)
  dontTouch(io.toMemory.ready)

  io.toMemory.bits.address := io.fromRegisterFile.bits.readData1 + io.fromIDU.bits.imm
  io.toMemory.bits.lenth := io.fromIDU.bits.memoryLenth
  io.toMemory.bits.writeData := io.fromRegisterFile.bits.readData2
  io.toMemory.bits.writeEnable := (io.fromIDU.bits.instructionType === InstType.S.asUInt)
  io.toMemory.valid := io.fromIDU.bits.memoryValid

  val data1 = MuxLookup(io.fromIDU.bits.data1Type, 0.U(32.W))(
    Seq(
      Data1Type.RS1.asUInt -> io.fromRegisterFile.bits.readData1,
      Data1Type.PC.asUInt -> io.currentPC
    )
  )

  val data2 = MuxLookup(io.fromIDU.bits.data2Type, 0.U(32.W))(
    Seq(
      Data2Type.RS2.asUInt -> io.fromRegisterFile.bits.readData2,
      Data2Type.IMM.asUInt -> io.fromIDU.bits.imm
    )
  )

  val memoryReadData = MuxLookup(io.fromIDU.bits.memoryLenth, 0.U(32.W))(
    Seq(
      MemLen.B.asUInt -> Fill(
        24,
        io.fromMemory.bits.readData(7) & ~io.fromIDU.bits.unsigned
      ) ## io.fromMemory.bits.readData(7, 0),
      MemLen.H.asUInt -> Fill(
        16,
        io.fromMemory.bits.readData(15) & ~io.fromIDU.bits.unsigned
      ) ## io.fromMemory.bits.readData(15, 0),
      MemLen.W.asUInt -> io.fromMemory.bits.readData
    )
  )

  val result = MuxLookup(io.fromIDU.bits.aluOp, 0.U(32.W))(
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

  val compareCheck = MuxLookup(io.fromIDU.bits.compareOp, false.B)(
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
    io.currentPC + io.fromIDU.bits.imm,
    io.currentPC + 4.U
  )

  io.nextPC := MuxLookup(
    io.fromIDU.bits.nextPCType,
    0.U(32.W)
  )(
    Seq(
      NextPCDataType.RESULT.asUInt -> (result & (~1.U(32.W))),
      NextPCDataType.BRANCH.asUInt -> branchTarget,
      NextPCDataType.CSRDATA.asUInt -> io.csrData,
      NextPCDataType.NORMAL.asUInt -> (io.currentPC + 4.U)
    )
  )

  io.toRegisterFile.bits.writeData := MuxLookup(
    io.fromIDU.bits.registerWriteType,
    0.U(32.W)
  )(
    Seq(
      RegWriteDataType.RESULT.asUInt -> result,
      RegWriteDataType.NEXTPC.asUInt -> (io.currentPC + 4.U),
      RegWriteDataType.MEMREAD.asUInt -> memoryReadData,
      RegWriteDataType.CSRDATA.asUInt -> io.csrData
    )
  )

  io.toRegisterFile.bits.writeEnable := Mux(
    (io.fromIDU.bits.instructionType === InstType.S.asUInt) || (io.fromIDU.bits.instructionType === InstType.B.asUInt),
    false.B,
    true.B
  )

  val powerManager = Module(new PowerManager())
  powerManager.io.reset := reset
  powerManager.io.breakSignal := io.fromIDU.bits.break
  powerManager.io.code := data1
}
