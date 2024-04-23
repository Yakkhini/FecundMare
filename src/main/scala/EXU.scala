package taohe

import chisel3._
import chisel3.util.MuxLookup

import taohe.util.EXUBundle
import taohe.idu.{Data1Type, Data2Type, ImmType, InstType, ALUOpType}
import chisel3.probe.Probe

class EXU extends Module {
  val io = IO(new EXUBundle)

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

  io.withRegisterFile.writeData := result
  io.withRegisterFile.writeEnable := true.B

  val powerManager = Module(new PowerManager())
  powerManager.io.reset := reset
  powerManager.io.breakSignal := io.fromIDU.break
  powerManager.io.code := data1
}
