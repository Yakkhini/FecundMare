package taohe

import chisel3._
import circt.stage.ChiselStage
import chisel3.probe.Probe

class TaoHe extends Module {
  val io = IO(new Bundle {})

  val pc = RegInit("h80000000".U(32.W))
  val pcIN = Wire(UInt(32.W))
  pcIN := pc + 4.U
  pc := pcIN
  dontTouch(pcIN)

  val inst = Wire(UInt(32.W))
  dontTouch(inst)

  val instFetchUnit = Module(new IFU())
  instFetchUnit.io.reset := reset
  instFetchUnit.io.pc := pc
  inst := instFetchUnit.io.inst

  val registerFile = Module(new RegisterFile())

  val IDU = Module(new idu.IDU())
  IDU.io.inst := inst
  registerFile.io.fromIDU <> IDU.io.controlSignal.toRegisterFile

  val EXU = Module(new EXU())
  EXU.io.fromIDU <> IDU.io.controlSignal.toEXU
  registerFile.io.withEXU <> EXU.io.withRegisterFile
  EXU.io.currentPC := pc

  dontTouch(EXU.io.withRegisterFile.writeData)
  dontTouch(EXU.io.withRegisterFile.writeEnable)

}

object Main extends App {
  println("Hello World, I will now generate the Verilog file!")
  ChiselStage.emitSystemVerilogFile(
    gen = new TaoHe(),
    args = Array("--target-dir", "out/verilog", "--split-verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )
}
