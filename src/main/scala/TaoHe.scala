package taohe

import chisel3._
import circt.stage.ChiselStage

import taohe.idu.IDU

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

  val idu = Module(new IDU())
  idu.io.inst := inst
  registerFile.io.fromIDU <> idu.io.toRegisterFile
  csr.io.address := idu.io.csrAddress
  csr.io.csrOperation := idu.io.csrOperation

  val exu = Module(new EXU())
  exu.io.currentPC := pc
  pc := exu.io.nextPC
  exu.io.csrData := csr.io.readData
  exu.io.fromIDU <> idu.io.toEXU
  exu.io.fromRegisterFile <> registerFile.io.toEXU
  exu.io.fromMemory <> memory.io.toEXU
  registerFile.io.fromEXU <> exu.io.toRegisterFile
  memory.io.fromEXU <> exu.io.toMemory

  dontTouch(pc)
  dontTouch(inst)
  dontTouch(exu.io.toRegisterFile.bits.writeData)
  dontTouch(exu.io.toRegisterFile.bits.writeEnable)
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
