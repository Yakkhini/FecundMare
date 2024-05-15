package taohe.util.enum

import chisel3._

object UnitState extends ChiselEnum {
  val IDLE, WAIT = Value
}

object InstType extends ChiselEnum {
  val R, I, S, B, U, J = Value
}

object ImmType extends ChiselEnum {
  val I, S, B, U, J = Value
}

object ALUOpType extends ChiselEnum {
  val ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU = Value
}

object CompareOpType extends ChiselEnum {
  val EQ, NE, LT, GE, LTU, GEU = Value
}

object Data1Type extends ChiselEnum {
  val PC, RS1 = Value
}

object Data2Type extends ChiselEnum {
  val IMM, RS2 = Value
}

object RegWriteDataType extends ChiselEnum {
  val RESULT, NEXTPC, MEMREAD, CSRDATA = Value
}

object NextPCDataType extends ChiselEnum {
  val RESULT, BRANCH, CSRDATA, NORMAL = Value
}

object MemLen extends ChiselEnum {
  val B = Value(1.U)
  val H = Value(2.U)
  val W = Value(4.U)
}

object CSREnum extends ChiselEnum {
  val MSTATUS = Value("h300".U(12.W))
  val MTVEC = Value("h305".U(12.W))
  val MEPC = Value("h341".U(12.W))
  val MCAUSE = Value("h342".U(12.W))
}

object CSROPType extends ChiselEnum {
  val RW, RS, RC, RET, CALL, NONE = Value
}
