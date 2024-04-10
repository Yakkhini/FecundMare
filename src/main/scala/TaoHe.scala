package taohe

import chisel3._, chisel3.util._

// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage

class TaoHe extends Module {
  val io = IO(new Bundle {
    val readAddr1 = Input(UInt(5.W))
    val readAddr2 = Input(UInt(5.W))
    val writeAddr = Input(UInt(5.W))
    val writeData = Input(UInt(32.W))
    val writeEnable = Input(Bool())
    val readData1 = Output(UInt(32.W))
    val readData2 = Output(UInt(32.W))
  })

  val pc = RegInit("h80000000".U(32.W))
  val pcIn = Wire(UInt(32.W))
  pcIn := pc + 4.U
  pc := pcIn
  dontTouch(pc)
  dontTouch(pcIn)

  val inst = Wire(UInt(32.W))
  dontTouch(inst)

  val instFetchUnit = Module(new IFU())
  instFetchUnit.io.pc := pc
  instFetchUnit.io.reset := reset
  inst := instFetchUnit.io.inst

  val regFile = Module(new RegisterFile())
  regFile.io.readAddr1 := io.readAddr1
  regFile.io.readAddr2 := io.readAddr2
  regFile.io.writeAddr := io.writeAddr
  regFile.io.writeData := io.writeData
  regFile.io.writeEnable := io.writeEnable
  io.readData1 := regFile.io.readData1
  io.readData2 := regFile.io.readData2

}

object Main extends App {
  println("Hello World, I will now generate the Verilog file!")
  ChiselStage.emitSystemVerilogFile(
    gen = new TaoHe(),
    args = Array("--target-dir", "out/verilog", "--split-verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )
}
