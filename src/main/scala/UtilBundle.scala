package taohe.util

import chisel3._

import taohe.util.enum._
import chisel3.util.Decoupled

class IDUToRegisterFileBundle extends Bundle {
  val readAddr1 = UInt(5.W)
  val readAddr2 = UInt(5.W)
  val writeAddr = UInt(5.W)
}

class IDUToEXUBundle extends Bundle {
  val instructionType = UInt(InstType.getWidth.W)
  val data1Type = UInt(Data1Type.getWidth.W)
  val data2Type = UInt(Data2Type.getWidth.W)
  val registerWriteType = UInt(RegWriteDataType.getWidth.W)
  val nextPCType = UInt(NextPCDataType.getWidth.W)
  val memoryLenth = UInt(MemLen.getWidth.W)
  val aluOp = UInt(ALUOpType.getWidth.W)
  val compareOp = UInt(CompareOpType.getWidth.W)
  val memoryValid = Bool()
  val unsigned = Bool()
  val break = Bool()
  val imm = UInt(32.W)
}

class RegisterFileToEXUBundle extends Bundle {
  val readData1 = UInt(32.W)
  val readData2 = UInt(32.W)
}

class EXUToRegisterFileBundle extends Bundle {
  val writeData = UInt(32.W)
  val writeEnable = Bool()
}

class MemoryToEXUBundle extends Bundle {
  val readData = UInt(32.W)
}

class EXUToMemoryBundle extends Bundle {
  val writeEnable = Bool()
  val writeData = UInt(32.W)
  val address = UInt(32.W)
  val lenth = UInt(32.W)
}

class MemoryBundle extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val fromEXU = Flipped(Decoupled(new EXUToMemoryBundle))
  val toEXU = Decoupled(new MemoryToEXUBundle)
}

class RegisterFileBundle extends Bundle {
  val fromIDU = Flipped(Decoupled(new IDUToRegisterFileBundle))
  val fromEXU = Flipped(Decoupled(new EXUToRegisterFileBundle))
  val toEXU = Decoupled(new RegisterFileToEXUBundle)
}

class IDUBundle extends Bundle {
  val inst = Input(UInt(32.W))
  val toRegisterFile = Decoupled(new IDUToRegisterFileBundle)
  val toEXU = Decoupled(new IDUToEXUBundle)
  val csrAddress = Output(UInt(12.W))
  val csrOperation = Output(UInt(CSROPType.getWidth.W))
}

class EXUBundle extends Bundle {
  val currentPC = Input(UInt(32.W))
  val nextPC = Output(UInt(32.W))
  val csrData = Input(UInt(32.W))
  val fromIDU = Flipped(Decoupled(new IDUToEXUBundle))
  val fromRegisterFile = Flipped(Decoupled(new RegisterFileToEXUBundle))
  val toRegisterFile = Decoupled(new EXUToRegisterFileBundle)
  val fromMemory = Flipped(Decoupled(new MemoryToEXUBundle))
  val toMemory = Decoupled(new EXUToMemoryBundle)
}
