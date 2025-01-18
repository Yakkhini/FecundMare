package taohe.util

import chisel3._

import taohe.util.enum._
import chisel3.util.Decoupled
import chisel3.experimental.dataview._

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

class AXI4AWChannel extends Bundle {
  val addr = Output(UInt(32.W))
  val id = Output(UInt(4.W))
  val len = Output(UInt(8.W))
  val size = Output(UInt(3.W))
  val burst = Output(UInt(2.W))
}

class AXI4WChannel extends Bundle {
  val data = Output(UInt(32.W))
  val strb = Output(UInt(4.W))
  val last = Output(Bool())
}

class AXI4BChannel extends Bundle {
  val id = Output(UInt(4.W))
  val resp = Output(UInt(2.W))
}

class AXI4ARChannel extends Bundle {
  val addr = Output(UInt(32.W))
  val id = Output(UInt(4.W))
  val len = Output(UInt(8.W))
  val size = Output(UInt(3.W))
  val burst = Output(UInt(2.W))
}

class AXI4RChannel extends Bundle {
  val id = Output(UInt(4.W))
  val data = Output(UInt(32.W))
  val resp = Output(UInt(2.W))
  val last = Output(Bool())
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

class AXI4Bundle extends Bundle {
  // Manager to Subordinate
  val aw = Decoupled(new AXI4AWChannel)
  val w = Decoupled(new AXI4WChannel)
  val b = Flipped(Decoupled(new AXI4BChannel))
  val ar = Decoupled(new AXI4ARChannel)
  val r = Flipped(Decoupled(new AXI4RChannel))
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

class YSYXSoCAXI4Bundle extends Bundle {
  val awready = Input(Bool())
  val awvalid = Output(Bool())
  val awaddr = Output(UInt(32.W))
  val awid = Output(UInt(4.W))
  val awlen = Output(UInt(8.W))
  val awsize = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))

  val wready = Input(Bool())
  val wvalid = Output(Bool())
  val wdata = Output(UInt(32.W))
  val wstrb = Output(UInt(4.W))
  val wlast = Output(Bool())

  val bready = Output(Bool())
  val bvalid = Input(Bool())
  val bresp = Input(UInt(2.W))
  val bid = Input(UInt(4.W))

  val arready = Input(Bool())
  val arvalid = Output(Bool())
  val araddr = Output(UInt(32.W))
  val arid = Output(UInt(4.W))
  val arlen = Output(UInt(8.W))
  val arsize = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))

  val rready = Output(Bool())
  val rvalid = Input(Bool())
  val rdata = Input(UInt(32.W))
  val rresp = Input(UInt(2.W))
  val rlast = Input(Bool())
  val rid = Input(UInt(4.W))
}

// Data views
object YSYXSoCAXI4Bundle {
  implicit val axiView: DataView[AXI4Bundle, YSYXSoCAXI4Bundle] = DataView(
    vab => new YSYXSoCAXI4Bundle(),
    _.aw.ready -> _.awready,
    _.aw.valid -> _.awvalid,
    _.aw.bits.addr -> _.awaddr,
    _.aw.bits.id -> _.awid,
    _.aw.bits.len -> _.awlen,
    _.aw.bits.size -> _.awsize,
    _.aw.bits.burst -> _.awburst,
    _.w.ready -> _.wready,
    _.w.valid -> _.wvalid,
    _.w.bits.data -> _.wdata,
    _.w.bits.strb -> _.wstrb,
    _.w.bits.last -> _.wlast,
    _.b.ready -> _.bready,
    _.b.valid -> _.bvalid,
    _.b.bits.resp -> _.bresp,
    _.b.bits.id -> _.bid,
    _.ar.ready -> _.arready,
    _.ar.valid -> _.arvalid,
    _.ar.bits.addr -> _.araddr,
    _.ar.bits.id -> _.arid,
    _.ar.bits.len -> _.arlen,
    _.ar.bits.size -> _.arsize,
    _.ar.bits.burst -> _.arburst,
    _.r.ready -> _.rready,
    _.r.valid -> _.rvalid,
    _.r.bits.data -> _.rdata,
    _.r.bits.resp -> _.rresp,
    _.r.bits.last -> _.rlast,
    _.r.bits.id -> _.rid
  )
}
