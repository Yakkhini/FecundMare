package taohe.util

import chisel3._

import taohe.util.enum._
import chisel3.util.Decoupled

// Internal interfaces
class IFUToIDUBundle extends Bundle {
  val currentPC = UInt(32.W)
  val inst = UInt(32.W)
}

class IDUToEXUBundle extends Bundle {
  val currentPC = UInt(32.W)
  val registerReadData1 = UInt(32.W)
  val registerReadData2 = UInt(32.W)
  val registerWriteAddr = UInt(5.W)
  val registerWriteType = UInt(RegWriteDataType.getWidth.W)
  val csrAddress = UInt(12.W)
  val csrOperation = UInt(CSROPType.getWidth.W)
  val instructionType = UInt(InstType.getWidth.W)
  val data1Type = UInt(Data1Type.getWidth.W)
  val data2Type = UInt(Data2Type.getWidth.W)
  val nextPCType = UInt(NextPCDataType.getWidth.W)
  val lsuLength = UInt(MemLen.getWidth.W)
  val aluOp = UInt(ALUOpType.getWidth.W)
  val compareOp = UInt(CompareOpType.getWidth.W)
  val lsuReadEnable = Bool()
  val lsuWriteEnable = Bool()
  val unsigned = Bool()
  val break = Bool()
  val imm = UInt(32.W)
}

class RegisterFileToIDUBundle extends Bundle {
  val readData1 = UInt(32.W)
  val readData2 = UInt(32.W)
}

class IDUToRegisterFileBundle extends Bundle {
  val readAddr1 = UInt(5.W)
  val readAddr2 = UInt(5.W)
}

class EXUToRegisterFileBundle extends Bundle {
  val writeAddr = UInt(5.W)
  val writeData = UInt(32.W)
  val writeEnable = Bool()
}

class CSRToEXUBundle extends Bundle {
  val readData = UInt(32.W)
}

class EXUToCSRBundle extends Bundle {
  val operation = UInt(CSROPType.getWidth.W)
  val address = UInt(12.W)
  val currentPC = UInt(32.W)
  val rs1data = UInt(32.W)
}

class EXUToIFUBundle extends Bundle {
  val nextPC = UInt(32.W)
}

class LSUToEXUBundle extends Bundle {
  val readData = UInt(32.W)
}

class EXUToLSUBundle extends Bundle {
  val readEnable = Bool()
  val writeEnable = Bool()
  val writeData = UInt(32.W)
  val address = UInt(32.W)
  val length = UInt(32.W)
}

class AXI4LiteAWChannel extends Bundle {
  val addr = Output(UInt(32.W))
  // Not required to differentiate between Non-secure and Secure accesses
  // val prot = Output(UInt(3.W))
}

class AXI4LiteWChannel extends Bundle {
  val data = Output(UInt(32.W))
  val strb = Output(UInt(4.W))
}

class AXI4LiteBChannel extends Bundle {
  val resp = Output(UInt(2.W))
}

class AXI4LiteARChannel extends Bundle {
  val addr = Output(UInt(32.W))
  // Not required to differentiate between Non-secure and Secure accesses
  // val prot = Output(UInt(3.W))
}

class AXI4LiteRChannel extends Bundle {
  val data = Output(UInt(32.W))
  val resp = Output(UInt(2.W))
}

// Public interfaces
class AXI4LiteBundle extends Bundle {
  // Manager to Subordinate
  val aw = Decoupled(new AXI4LiteAWChannel)
  val w = Decoupled(new AXI4LiteWChannel)
  val b = Flipped(Decoupled(new AXI4LiteBChannel))
  val ar = Decoupled(new AXI4LiteARChannel)
  val r = Flipped(Decoupled(new AXI4LiteRChannel))
}

class LSUBundle extends Bundle {
  val fromEXU = Flipped(Decoupled(new EXUToLSUBundle))
  val toEXU = Decoupled(new LSUToEXUBundle)
  val axi4Lite = new AXI4LiteBundle
}

class RegisterFileBundle extends Bundle {
  val fromEXU = Flipped(Decoupled(new EXUToRegisterFileBundle))
  val fromIDU = Flipped(Decoupled(new IDUToRegisterFileBundle))
  val toIDU = Decoupled(new RegisterFileToIDUBundle)
}

class CSRBundle extends Bundle {
  val fromEXU = Flipped(Decoupled(new EXUToCSRBundle))
  val toEXU = Decoupled(new CSRToEXUBundle)
}

class IFUBundle extends Bundle {
  val fromEXU = Flipped(Decoupled(new EXUToIFUBundle))
  val toIDU = Decoupled(new IFUToIDUBundle)
  val axi4Lite = new AXI4LiteBundle
}

class IDUBundle extends Bundle {
  val fromIFU = Flipped(Decoupled(new IFUToIDUBundle))
  val toEXU = Decoupled(new IDUToEXUBundle)
  val fromRegisterFile = Flipped(Decoupled(new RegisterFileToIDUBundle))
  val toRegisterFile = Decoupled(new IDUToRegisterFileBundle)
}

class EXUBundle extends Bundle {
  val fromIDU = Flipped(Decoupled(new IDUToEXUBundle))
  val toRegisterFile = Decoupled(new EXUToRegisterFileBundle)
  val fromLSU = Flipped(Decoupled(new LSUToEXUBundle))
  val toLSU = Decoupled(new EXUToLSUBundle)
  val fromCSR = Flipped(Decoupled(new CSRToEXUBundle))
  val toCSR = Decoupled(new EXUToCSRBundle)
  val toIFU = Decoupled(new EXUToIFUBundle)
}
