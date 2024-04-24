package taohe.util

import chisel3._
import taohe.idu.{
  ALUOpType,
  Data1Type,
  Data2Type,
  RegWriteDataType,
  MemLen,
  ImmType,
  InstType
}

class RegisterFileBundle extends Bundle {
  val fromIDU = Flipped(new ControlSignalToRegisterFileBundle)
  val withEXU = new RegisterFileEXUBundle
}

class ControlSignalToRegisterFileBundle extends Bundle {
  val readAddr1 = Output(UInt(5.W))
  val readAddr2 = Output(UInt(5.W))
  val writeAddr = Output(UInt(5.W))
}

class ControlSignalToEXUBundle extends Bundle {
  val instructionType = Output(UInt(InstType.getWidth.W))
  val data1Type = Output(UInt(Data1Type.getWidth.W))
  val data2Type = Output(UInt(Data2Type.getWidth.W))
  val registerWriteType = Output(UInt(RegWriteDataType.getWidth.W))
  val memoryLenth = Output(UInt(MemLen.getWidth.W))
  val aluOp = Output(UInt(ALUOpType.getWidth.W))
  val jump = Output(Bool())
  val break = Output(Bool())
  val imm = Output(UInt(32.W))
}

class ControlSignalBundle extends Bundle {
  val toRegisterFile = new ControlSignalToRegisterFileBundle
  val toEXU = new ControlSignalToEXUBundle
}

class RegisterFileEXUBundle extends Bundle {
  val writeData = Input(UInt(32.W))
  val writeEnable = Input(Bool())
  val readData1 = Output(UInt(32.W))
  val readData2 = Output(UInt(32.W))
}

class MemoryEXUBundle extends Bundle {
  val writeEnable = Input(Bool())
  val writeData = Input(UInt(32.W))
  val readData = Output(UInt(32.W))
  val address = Input(UInt(32.W))
  val lenth = Input(UInt(32.W))
}

class MemoryBundle extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val valid = Input(Bool())
  val withEXU = new MemoryEXUBundle
}

class IDUBundle extends Bundle {
  val inst = Input(UInt(32.W))
  val controlSignal = new ControlSignalBundle
  val memoryValid = Output(Bool())
}

class EXUBundle extends Bundle {
  val currentPC = Input(UInt(32.W))
  val nextPC = Output(UInt(32.W))
  val fromIDU = Flipped(new ControlSignalToEXUBundle)
  val withRegisterFile = Flipped(new RegisterFileEXUBundle)
  val withMemory = Flipped(new MemoryEXUBundle)
}
