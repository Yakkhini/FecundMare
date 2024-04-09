package TaoHe

import chisel3._, chisel3.util._

class TaoHe extends Module {
  val io = IO(new Bundle {})

}

object Main extends App {
  println("Hello World, I will now generate the Verilog file!")
  emitVerilog(new TaoHe, Array("--target-dir", "out/verilog"))
}
