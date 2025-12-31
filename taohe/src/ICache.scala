package taohe

import chisel3._
import chisel3.util.{switch, is}
import chisel3.util.Cat

import taohe.util.ICacheBundle
import taohe.util.enum.MemSize
import taohe.util.PerformanceCounter

object ICacheState extends ChiselEnum {
  /*
   * ICache FSM State
   *
   * 1. Ready State: ICache is waiting IFU request
   * and could know cache hit or miss in same cycle.
   * 2. Request State: When hit miss, send request
   * to memory via AXI4 interface.
   * 3. Fetch State: After AR channel fire, waiting
   * respond from bus.
   * 4. Send State: Send back instruction data to IFU,
   * and write data to cache line when cache miss.
   *
   * Cache Hit FSM:
   * Ready State -> Send State
   *
   * Cache Miss FSM:
   * Ready State -> Request State -> Fetch State -> Send State
   *
   * */
  val sReady, sRequest, sFetch, sSend = Value
}

/*
 * Cache Structure:
 * 
 * 16 x [[1 bit Valid] [26 bit Tag] [4 bit Index] [32 bit data]] Cache Line
 *
 * */
class ICache extends Module {

  val io = IO(new ICacheBundle)
  val state = RegInit(ICacheState.sReady)

  val cache = RegInit(VecInit(Seq.fill(16)(0.U(59.W))))

  // Ready State
  io.fromIFU.ready := state === ICacheState.sReady

  val pcBuffer = RegInit(0.U(32.W))
  pcBuffer := Mux(io.fromIFU.fire, io.fromIFU.bits.pc, pcBuffer)

  val index =
    Mux(state === ICacheState.sReady, io.fromIFU.bits.pc(5, 2), pcBuffer(5, 2))
  val readCacheLine = cache(index)

  val cacheHit = RegInit(false.B)
  val cacheReadData = RegInit(0.U(32.W))
  val readValid = readCacheLine(58)
  val readTag = readCacheLine(57, 32)

  cacheHit := Mux(
    io.fromIFU.fire,
    readValid && (readTag === io.fromIFU.bits.pc(31, 6)),
    cacheHit
  )
  cacheReadData := readCacheLine(31, 0)

  // Request State
  io.axi4.ar.valid := state === ICacheState.sRequest
  io.axi4.ar.bits.addr := pcBuffer
  io.axi4.ar.bits.id := 0.U
  io.axi4.ar.bits.len := 0.U
  io.axi4.ar.bits.size := MemSize.W.asUInt
  io.axi4.ar.bits.burst := 0.U

  // Fetch State
  io.axi4.r.ready := state === ICacheState.sFetch

  val memoryReadData = RegInit(0.U(32.W))
  memoryReadData := Mux(io.axi4.r.fire, io.axi4.r.bits.data, memoryReadData)

  // Send State
  io.toIFU.valid := state === ICacheState.sSend
  io.toIFU.bits.readInst := Mux(cacheHit, cacheReadData, memoryReadData)

  cache(index) := Mux(
    !cacheHit && state === ICacheState.sSend,
    Cat(1.U(1.W), pcBuffer(31, 6), memoryReadData),
    readCacheLine
  )

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
  val iCacheHitCounter = PerformanceCounter(
    io.fromIFU.fire && readValid && (readTag === io.fromIFU.bits.pc(31, 6)),
    32
  )

  switch(state) {
    is(ICacheState.sReady) {
      when(io.fromIFU.fire) {
        state := Mux(
          readValid && (readTag === io.fromIFU.bits.pc(31, 6)),
          ICacheState.sSend,
          ICacheState.sRequest
        )
      }
    }
    is(ICacheState.sRequest) {
      when(io.axi4.ar.fire) {
        state := ICacheState.sFetch
      }
    }
    is(ICacheState.sFetch) {
      when(io.axi4.r.fire) {
        state := ICacheState.sSend
      }
    }
    is(ICacheState.sSend) {
      when(io.toIFU.fire) {
        state := ICacheState.sReady
      }
    }
  }

}
