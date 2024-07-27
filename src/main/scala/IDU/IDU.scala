package taohe.idu

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup

import taohe.util.enum._
import taohe.util.IDUBundle

class IDU extends Module {
  val io = IO(new IDUBundle)

  val pc = RegInit(0.U(32.W))
  val inst = RegInit(0.U(32.W))

  io.fromIFU.ready := true.B
  pc := Mux(io.fromIFU.fire, io.fromIFU.bits.currentPC, pc)
  inst := Mux(io.fromIFU.fire, io.fromIFU.bits.inst, inst)

  io.toEXU.valid := true.B

  import IDUTable.decodeTable

  val decodeResult = decodeTable.decode(inst)

  io.toEXU.bits.currentPC := pc

  val imm_i = inst(31) ## Fill(20, inst(31)) ## inst(30, 20)
  val imm_s = inst(31) ## Fill(20, inst(31)) ## inst(30, 25) ## inst(11, 7)
  val imm_b = inst(31) ## Fill(19, inst(31)) ## inst(7) ##
    inst(30, 25) ## inst(11, 8) ## 0.U(1.W)
  val imm_u = inst(31, 12) ## 0.U(12.W)
  val imm_j = inst(31) ## Fill(11, inst(31)) ## inst(19, 12) ## inst(
    20
  ) ## inst(30, 21) ## 0.U(1.W)

  val immType = decodeResult(ImmField)

  io.toEXU.bits.imm := MuxLookup(immType, 0.U)(
    Seq(
      ImmType.I.asUInt -> imm_i,
      ImmType.S.asUInt -> imm_s,
      ImmType.B.asUInt -> imm_b,
      ImmType.U.asUInt -> imm_u,
      ImmType.J.asUInt -> imm_j
    )
  )

  val breakReadAddr = MuxLookup(
    inst(31, 20),
    inst(19, 15)
  )(
    Seq(
      "b000000000001".U -> 10.U
    )
  )

  io.toRegisterFile.bits.readAddr1 := MuxLookup(
    inst(6, 0),
    inst(19, 15)
  )(
    Seq(
      "b0110111".U -> 0.U,
      "b1110011".U -> breakReadAddr
    )
  )
  io.toRegisterFile.bits.readAddr2 := inst(24, 20)

  io.toEXU.bits.registerReadData1 := io.fromRegisterFile.bits.readData1
  io.toEXU.bits.registerReadData2 := io.fromRegisterFile.bits.readData2
  io.toEXU.bits.registerWriteAddr := inst(11, 7)

  io.toEXU.bits.instructionType := decodeResult(InstTypeField)
  io.toEXU.bits.data1Type := decodeResult(Data1Field)
  io.toEXU.bits.data2Type := decodeResult(Data2Field)
  io.toEXU.bits.registerWriteType := decodeResult(
    RegWriteDataTypeField
  )
  io.toEXU.bits.nextPCType := decodeResult(NextPCDataTypeField)
  io.toEXU.bits.lsuLength := decodeResult(MemLenField)
  io.toEXU.bits.aluOp := decodeResult(ALUOpField)
  io.toEXU.bits.compareOp := decodeResult(CompareOpField)
  io.toEXU.bits.unsigned := decodeResult(UnsignField)
  io.toEXU.bits.break := decodeResult(BreakField)

  io.toEXU.bits.lsuReadEnable := inst(6, 0) === "b0000011".U
  io.toEXU.bits.lsuWriteEnable := inst(6, 0) === "b0100011".U

  io.toEXU.bits.csrAddress := inst(31, 20)
  io.toEXU.bits.csrOperation := decodeResult(CSROPTypeField)

  val decodeSupport = Wire(Bool())
  decodeSupport := decodeResult(DecodeSupportField) | ~io.fromIFU.valid
  dontTouch(decodeSupport)
}
