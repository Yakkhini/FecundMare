package taohe.idu

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup

import taohe.util.enum._
import taohe.util.IDUBundle

class IDU extends Module {
  val io = IO(new IDUBundle)

  io.toEXU.valid := io.fromIFU.valid
  io.fromIFU.ready := true.B

  import IDUTable.decodeTable

  val decodeResult = decodeTable.decode(io.fromIFU.bits.inst)

  io.toEXU.bits.currentPC := io.fromIFU.bits.currentPC

  val imm_i = io.fromIFU.bits.inst(31) ## Fill(
    20,
    io.fromIFU.bits.inst(31)
  ) ## io.fromIFU.bits.inst(30, 20)
  val imm_s =
    io.fromIFU.bits.inst(31) ## Fill(
      20,
      io.fromIFU.bits.inst(31)
    ) ## io.fromIFU.bits.inst(30, 25) ## io.fromIFU.bits.inst(11, 7)
  val imm_b =
    io.fromIFU.bits.inst(31) ## Fill(
      19,
      io.fromIFU.bits.inst(31)
    ) ## io.fromIFU.bits.inst(7) ## io.fromIFU.bits
      .inst(30, 25) ## io.fromIFU.bits.inst(11, 8) ## 0.U(1.W)
  val imm_u = io.fromIFU.bits.inst(31, 12) ## 0.U(12.W)
  val imm_j =
    io.fromIFU.bits.inst(31) ## Fill(
      11,
      io.fromIFU.bits.inst(31)
    ) ## io.fromIFU.bits.inst(19, 12) ## io.fromIFU.bits.inst(
      20
    ) ## io.fromIFU.bits.inst(30, 21) ## 0.U(1.W)

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
    io.fromIFU.bits.inst(31, 20),
    io.fromIFU.bits.inst(19, 15)
  )(
    Seq(
      "b000000000001".U -> 10.U
    )
  )

  io.toEXU.bits.registerReadAddr1 := MuxLookup(
    io.fromIFU.bits.inst(6, 0),
    io.fromIFU.bits.inst(19, 15)
  )(
    Seq(
      "b0110111".U -> 0.U,
      "b1110011".U -> breakReadAddr
    )
  )
  io.toEXU.bits.registerReadAddr2 := io.fromIFU.bits.inst(24, 20)
  io.toEXU.bits.registerWriteAddr := io.fromIFU.bits.inst(11, 7)

  io.toEXU.bits.instructionType := decodeResult(InstTypeField)
  io.toEXU.bits.data1Type := decodeResult(Data1Field)
  io.toEXU.bits.data2Type := decodeResult(Data2Field)
  io.toEXU.bits.registerWriteType := decodeResult(
    RegWriteDataTypeField
  )
  io.toEXU.bits.nextPCType := decodeResult(NextPCDataTypeField)
  io.toEXU.bits.lsuLenth := decodeResult(MemLenField)
  io.toEXU.bits.lsuValid := decodeResult(MemValidField)
  io.toEXU.bits.aluOp := decodeResult(ALUOpField)
  io.toEXU.bits.compareOp := decodeResult(CompareOpField)
  io.toEXU.bits.unsigned := decodeResult(UnsignField)
  io.toEXU.bits.break := decodeResult(BreakField)

  io.toEXU.bits.csrAddress := io.fromIFU.bits.inst(31, 20)
  io.toEXU.bits.csrOperation := decodeResult(CSROPTypeField)

  val decodeSupport = Wire(Bool())
  decodeSupport := decodeResult(DecodeSupportField) | ~io.fromIFU.valid
  dontTouch(decodeSupport)
}
