package taohe.idu

import chisel3._
import chisel3.util.MuxLookup
import taohe.idu.ImmType.S
import taohe.idu.ImmType.U
import taohe.util.IDUBundle

class IDU extends Module {
  val io = IO(new IDUBundle)

  import IDUTable.decodeTable

  val decodeResult = decodeTable.decode(io.inst)

  val imm_i = io.inst(31) ## 0.U(20.W) ## io.inst(30, 20)
  val imm_s = io.inst(31) ## 0.U(20.W) ## io.inst(30, 25) ## io.inst(11, 7)
  val imm_b = io.inst(31) ## 0.U(19.W) ## io.inst(7) ## io.inst(30, 25) ## io
    .inst(11, 8) ## 0.U(1.W)
  val imm_u = io.inst(31, 12) ## 0.U(12.W)
  val imm_j = io.inst(31) ## 0.U(10.W) ## io.inst(19, 12) ## io.inst(20) ## io
    .inst(30, 21) ## 0.U(1.W)

  val immType = decodeResult(ImmField)

  io.controlSignal.toEXU.imm := MuxLookup(immType, 0.U)(
    Seq(
      ImmType.I.asUInt -> imm_i,
      ImmType.S.asUInt -> imm_s,
      ImmType.B.asUInt -> imm_b,
      ImmType.U.asUInt -> imm_u,
      ImmType.J.asUInt -> imm_j
    )
  )

  io.controlSignal.toRegisterFile.readAddr1 := io.inst(19, 15)
  io.controlSignal.toRegisterFile.readAddr2 := io.inst(24, 20)
  io.controlSignal.toRegisterFile.writeAddr := io.inst(11, 7)

  io.controlSignal.toEXU.data1Type := decodeResult(Data1Field)
  io.controlSignal.toEXU.data2Type := decodeResult(Data2Field)
  io.controlSignal.toEXU.aluOp := decodeResult(ALUOpField)
}
