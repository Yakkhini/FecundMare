package taohe

import chisel3._
import circt.stage.ChiselStage
import chisel3.probe.Probe

class TaoHe extends Module {
  val io = IO(new Bundle {})

  val pc = RegInit("h80000000".U(32.W))

  val inst = Wire(UInt(32.W))

  val instFetchUnit = Module(new IFU())
  instFetchUnit.io.reset := reset
  instFetchUnit.io.pc := pc
  inst := instFetchUnit.io.inst

  val registerFile = Module(new RegisterFile())
  val memory = Module(new Memory())
  memory.io.clock := clock
  memory.io.reset := reset

  val csr = Module(new CSR())
  csr.io.currentPC := pc
  csr.io.rs1data := registerFile.io.toEXU.bits.readData1

  val IDU = Module(new idu.IDU())
  IDU.io.inst := inst
  registerFile.io.fromIDU <> IDU.io.toRegisterFile
  csr.io.address := IDU.io.csrAddress
  csr.io.csrOperation := IDU.io.csrOperation

  val EXU = Module(new EXU())
  EXU.io.currentPC := pc
  pc := EXU.io.nextPC
  EXU.io.csrData := csr.io.readData
  EXU.io.fromIDU <> IDU.io.toEXU
  EXU.io.fromRegisterFile <> registerFile.io.toEXU
  EXU.io.fromMemory <> memory.io.toEXU
  registerFile.io.fromEXU <> EXU.io.toRegisterFile
  memory.io.fromEXU <> EXU.io.toMemory

  dontTouch(pc)
  dontTouch(inst)
  dontTouch(EXU.io.toRegisterFile.bits.writeData)
  dontTouch(EXU.io.toRegisterFile.bits.writeEnable)
  dontTouch(csr.io.readData)

}

object Main extends App {
  println("Hello World, I will now generate the Verilog file!")
  ChiselStage.emitSystemVerilogFile(
    gen = new TaoHe(),
    args = Array("--target-dir", "out/verilog", "--split-verilog"),
    firtoolOpts = Array("-preserve-aggregate=1d-vec")
  )
}
