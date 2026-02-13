package taohe

import scala.math

import chisel3._
import chisel3.util.{switch, is}
import chisel3.util.Cat
import chisel3.ltl.{AssertProperty, Sequence}
import chisel3.layer.elideBlocks

import taohe.util.ICacheBundle
import taohe.util.enum.MemSize
import taohe.util.PreSiliconPerformanceCounter

object ICacheState extends ChiselEnum {
  /*
   * ICache FSM State
   *
   * 1. Work State: ICache respond & receive next request
   * and could know cache hit or miss in same cycle.
   * 2. Request State: When hit miss, send request
   * to memory via AXI4 interface in burst read.
   * 3. Fetch State: After AR channel fire, waiting
   * respond from bus and write back to cache line.
   *
   * Cache Hit FSM:
   * Work State -> Work State
   *
   * Cache Miss FSM:
   * Work State -> Request State -> Fetch State -> Work State
   *
   * */
  val sWork, sRequest, sFetch = Value
}

/*
 * Configurable Cache Structure:
 * 
 * pow(2, indexWidth) x [[1 bit Valid] [tagWidth] [indexWidth] [32 bit data * pow(2, offsetWidth - 2)]] Cache Line
 *
 * */
class ICache(indexWidth: Int, offsetWidth: Int) extends Module {

  val io = IO(new ICacheBundle)
  val state = RegInit(ICacheState.sWork)

  val tagWidth = 32 - indexWidth - offsetWidth
  val cachelineWidth =
    1 + tagWidth + 32 * math.pow(2, (offsetWidth - 2)).toInt
  val cache = RegInit(
    VecInit(
      Seq.fill(math.pow(2, indexWidth).toInt)(0.U(cachelineWidth.W))
    )
  )

  // Work State
  io.fromIFU.ready := state === ICacheState.sWork

  val pcBuffer = RegInit(0.U(32.W))
  pcBuffer := Mux(io.fromIFU.fire, io.fromIFU.bits.pc, pcBuffer)

  val index = pcBuffer(indexWidth + offsetWidth - 1, offsetWidth)
  val readCacheLine = cache(index)

  val cacheHit = Wire(Bool())
  val cacheReadData = Wire(Vec(math.pow(2, offsetWidth - 2).toInt, UInt(32.W)))
  val readValid = readCacheLine(cachelineWidth - 1)
  val readTag = readCacheLine(cachelineWidth - 2, cachelineWidth - 1 - tagWidth)

  cacheHit := readValid && (readTag === pcBuffer(31, 32 - tagWidth))

  for (i <- 0 until math.pow(2, offsetWidth - 2).toInt) {
    cacheReadData(i) :=
      readCacheLine((i + 1) * 32 - 1, 0 + i * 32)
  }

  // Request State
  val readCount = RegInit(0.U((offsetWidth - 2).W))
  io.axi4.ar.bits.burst := 1.U // INCR Address Type
  io.axi4.ar.bits.len := (math.pow(2, offsetWidth - 2).toInt - 1).U // 4 beats
  io.axi4.ar.bits.size := MemSize.W.asUInt
  io.axi4.ar.valid := state === ICacheState.sRequest
  io.axi4.ar.bits.addr := pcBuffer(31, offsetWidth) << offsetWidth

  io.axi4.ar.bits.id := 0.U

  // Fetch State
  io.axi4.r.ready := state === ICacheState.sFetch

  val readCountNext = Mux(io.axi4.r.fire, readCount + 1.U, readCount)
  readCount := Mux(
    state === ICacheState.sRequest || state === ICacheState.sFetch,
    readCountNext,
    0.U
  )

  val memoryReadData = Wire(
    Vec(math.pow(2, offsetWidth - 2).toInt, UInt(32.W))
  )

  for (i <- 0 until math.pow(2, offsetWidth - 2).toInt) {
    memoryReadData(i) := Mux(
      i.U === readCount,
      io.axi4.r.bits.data,
      cacheReadData(i)
    )
  }

  // Send
  val offset = {
    if (offsetWidth == 2) 0.U
    else pcBuffer(offsetWidth - 1, 2)
  }

  val nopInstruction = "h00000013".U
  val readInst = Mux(cacheHit, cacheReadData(offset), memoryReadData(offset))
  io.toIFU.valid := state === ICacheState.sWork && cacheHit
  io.toIFU.bits.readInst := Mux(
    readInst === "h0000100f".U,
    nopInstruction,
    readInst
  )

  // FENCE.I Handle
  val receiveFENCEIInstruction = io.toIFU.fire && readInst === "h0000100f".U

  for (i <- 0 until math.pow(2, indexWidth).toInt) {
    val cacheLineFreshData =
      Mux(receiveFENCEIInstruction, 0.U(cachelineWidth.W), cache(i))

    cache(i) := Mux(
      !cacheHit && state === ICacheState.sFetch && io.axi4.r.fire && i.U === index && !receiveFENCEIInstruction,
      Cat(
        io.axi4.r.bits.last.asBool,
        pcBuffer(31, 32 - tagWidth),
        memoryReadData.asUInt
      ),
      cacheLineFreshData
    )
  }

  // Make write transaction silent
  io.axi4.aw.valid := false.B
  io.axi4.aw.bits.burst := 0.U
  io.axi4.aw.bits.addr := 0.U
  io.axi4.aw.bits.id := 0.U
  io.axi4.aw.bits.len := 0.U
  io.axi4.aw.bits.size := 0.U
  io.axi4.w.valid := false.B
  io.axi4.w.bits.data := 0.U
  io.axi4.w.bits.strb := 0.U
  io.axi4.w.bits.last := false.B
  io.axi4.b.ready := false.B

  // Performance Counter
  PreSiliconPerformanceCounter(
    "iCacheHitCounter",
    io.fromIFU.fire && readValid && (readTag === io.fromIFU.bits
      .pc(31, 32 - tagWidth)),
    32
  )
  PreSiliconPerformanceCounter(
    "iCacheMissCounter",
    io.fromIFU.fire && !(readValid && (readTag === io.fromIFU.bits
      .pc(31, 32 - tagWidth))),
    32
  )
  PreSiliconPerformanceCounter(
    "iCacheTMTCounter",
    state === ICacheState.sRequest || state === ICacheState.sFetch,
    32
  )

  switch(state) {
    is(ICacheState.sWork) {
      when(io.fromIFU.fire || !cacheHit) {
        state := Mux(cacheHit, ICacheState.sWork, ICacheState.sRequest)
      }
    }
    is(ICacheState.sRequest) {
      when(io.axi4.ar.fire) {
        state := ICacheState.sFetch
      }
    }
    is(ICacheState.sFetch) {
      when(
        io.axi4.r.fire && (readCount === math
          .pow(2, offsetWidth - 2)
          .toInt
          .U - 1.U)
      ) {
        state := ICacheState.sWork
      }
    }
  }

}

class ICacheTest extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(8.W))
    val valid = Input(Bool())
  })

  val dut = Module(new ICache(4, 2))

  dut.io.fromIFU.valid := io.valid && !reset.asBool
  dut.io.fromIFU.bits.pc := 0.U(22.W) ## io.addr ## 0.U(2.W)

  val pc = RegInit(0.U(32.W))
  val waiting = RegInit(false.B)

  pc := Mux(dut.io.fromIFU.fire, 0.U(22.W) ## io.addr ## 0.U(2.W), pc)
  when(dut.io.toIFU.fire) {
    waiting := false.B
  }.elsewhen(dut.io.fromIFU.fire) {
    waiting := true.B
  }

  dut.io.toIFU.ready := waiting

  val memory = RegInit(VecInit(Seq.tabulate(256)(i => i.U(32.W))))

  val cacheRequesting = RegInit(false.B)
  val cacheRequestAddr = RegInit(0.U(32.W))
  cacheRequestAddr := Mux(
    dut.io.axi4.ar.fire,
    dut.io.axi4.ar.bits.addr,
    cacheRequestAddr
  )
  when(dut.io.axi4.ar.fire) {
    cacheRequesting := true.B
  }.elsewhen(dut.io.axi4.r.fire) {
    cacheRequesting := false.B
  }

  dut.io.axi4.ar.ready := !cacheRequesting
  dut.io.axi4.r.valid := cacheRequesting
  dut.io.axi4.r.bits.data := memory(cacheRequestAddr(9, 2))

  val refData = memory(pc(9, 2))

  dut.io.axi4.r.bits.id := 0.U
  dut.io.axi4.r.bits.resp := 0.U
  dut.io.axi4.r.bits.last := false.B
  dut.io.axi4.aw.ready := false.B
  dut.io.axi4.w.ready := false.B
  dut.io.axi4.b.valid := false.B
  dut.io.axi4.b.bits.resp := 0.U
  dut.io.axi4.b.bits.id := 0.U

  val receiveCorrect =
    dut.io.toIFU.fire && (dut.io.toIFU.bits.readInst === refData)
  val satisfy = (!dut.io.toIFU.fire) || receiveCorrect
  dontTouch(satisfy)

  elideBlocks {
    AssertProperty(
      // prop = Sequence.BoolSequence(dut.io.toIFU.fire) |-> Sequence.BoolSequence(dut.io.toIFU.bits.readInst === refData),
      prop = Sequence.BoolSequence(satisfy),
      clock = None,
      label = Some("basic"),
      disable = None
    )
  }
}
