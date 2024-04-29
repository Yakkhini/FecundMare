package taohe.idu

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup

import taohe.util.enum._
import taohe.util.IDUBundle

class IDU extends Module {
  val io = IO(new IDUBundle)

  io.toEXU.valid := true.B
  io.toRegisterFile.valid := true.B

  import IDUTable.decodeTable

  val decodeResult = decodeTable.decode(io.inst)

  val imm_i = io.inst(31) ## Fill(20, io.inst(31)) ## io.inst(30, 20)
  val imm_s =
    io.inst(31) ## Fill(20, io.inst(31)) ## io.inst(30, 25) ## io.inst(11, 7)
  val imm_b =
    io.inst(31) ## Fill(19, io.inst(31)) ## io.inst(7) ## io.inst(30, 25) ## io
      .inst(11, 8) ## 0.U(1.W)
  val imm_u = io.inst(31, 12) ## 0.U(12.W)
  val imm_j =
    io.inst(31) ## Fill(11, io.inst(31)) ## io.inst(19, 12) ## io.inst(20) ## io
      .inst(30, 21) ## 0.U(1.W)

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
    io.inst(31, 20),
    io.inst(19, 15)
  )(
    Seq(
      "b000000000001".U -> 10.U
    )
  )

  io.toRegisterFile.bits.readAddr1 := MuxLookup(
    io.inst(6, 0),
    io.inst(19, 15)
  )(
    Seq(
      "b0110111".U -> 0.U,
      "b1110011".U -> breakReadAddr
    )
  )
  io.toRegisterFile.bits.readAddr2 := io.inst(24, 20)
  io.toRegisterFile.bits.writeAddr := io.inst(11, 7)

  io.toEXU.bits.instructionType := decodeResult(InstTypeField)
  io.toEXU.bits.data1Type := decodeResult(Data1Field)
  io.toEXU.bits.data2Type := decodeResult(Data2Field)
  io.toEXU.bits.registerWriteType := decodeResult(
    RegWriteDataTypeField
  )
  io.toEXU.bits.nextPCType := decodeResult(NextPCDataTypeField)
  io.toEXU.bits.memoryLenth := decodeResult(MemLenField)
  io.toEXU.bits.memoryValid := decodeResult(MemValidField)
  io.toEXU.bits.aluOp := decodeResult(ALUOpField)
  io.toEXU.bits.compareOp := decodeResult(CompareOpField)
  io.toEXU.bits.unsigned := decodeResult(UnsignField)
  io.toEXU.bits.break := decodeResult(BreakField)

  io.csrAddress := io.inst(31, 20)
  io.csrOperation := decodeResult(CSROPTypeField)

  val decodeSupport = Wire(Bool())
  decodeSupport := decodeResult(DecodeSupportField)
  dontTouch(decodeSupport)
}
