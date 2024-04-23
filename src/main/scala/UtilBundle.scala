package taohe.util

import chisel3._
import taohe.idu.{ALUOpType, Data1Type, Data2Type, ImmType, InstType}

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
  val data1Type = Output(UInt(Data1Type.getWidth.W))
  val data2Type = Output(UInt(Data2Type.getWidth.W))
  val aluOp = Output(UInt(ALUOpType.getWidth.W))
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

class IDUBundle extends Bundle {
  val inst = Input(UInt(32.W))
  val controlSignal = new ControlSignalBundle
}

class EXUBundle extends Bundle {
  val currentPC = Input(UInt(32.W))
  val fromIDU = Flipped(new ControlSignalToEXUBundle)
  val withRegisterFile = Flipped(new RegisterFileEXUBundle)
}
