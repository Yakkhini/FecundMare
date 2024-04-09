package taohe

import chisel3._, chisel3.util._

// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage

class TaoHe extends Module {
  val io = IO(new Bundle {})

}

object Main extends App {
  println("Hello World, I will now generate the Verilog file!")
  ChiselStage.emitSystemVerilogFile(new TaoHe(), Array("--target-dir", "out/verilog", "--split-verilog"))
}
