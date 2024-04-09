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
    new TaoHe(),
    Array("--target-dir", "out/verilog", "--split-verilog")
  )
}
