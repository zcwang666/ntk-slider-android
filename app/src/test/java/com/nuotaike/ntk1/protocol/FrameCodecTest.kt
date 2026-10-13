package com.nuotaike.ntk1.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议层的黄金用例回归。
 *
 * 这些用例把**官方小程序里的原始字节**固化成期望值，
 * 只要它们通过，就证明本 App 与官方小程序的蓝牙行为逐字节一致。
 */
class FrameCodecTest {

    /** 官方 `app-service.js` 中出现的全部 78 个命令字（2 字节）。 */
    private val officialOpcodes = setOf(
        "E7B6", "E7B9", "E6B9", "A9B9", "FFB9", "B5B9", "A8B6", "B4B8", "B6BA", "B2B6",
        "B8BC", "B1B5", "DDBA", "C1B6", "C0B6", "A3B6", "A2B6", "1CB6", "1BB6",
        "E4B6", "E2B6", "E0B6", "E5B6", "E3B6", "E1B6",
        "D1B6", "D3B6", "D5B6", "D2B6", "D4B6", "D6B6",
        "01B6", "A4B6", "03B6", "02B6", "A5B6", "04B6",
        "ABB8", "05B8", "07B8", "ACB8", "06B8", "08B8",
        "11B8", "12B8", "13B8", "14B8", "15B8", "16B8", "17B8", "18B8",
        "19B8", "1AB8", "20B8", "21B8",
        "C9B6", "CAB6", "CBB6", "CCB6",
        "AAB6", "25B8", "23B8", "24B8", "ECB8",
        "A7B6", "C5B6", "A6B6", "C6B6", "B7BA", "DCBA", "D9B6", "D8B6", "22BA", "D0B6",
        "E8B6", "E9B6", "EAB6", "EBB6",
    )

    @Test
    fun `指令总数与官方一致`() {
        assertEquals(78, SliderCommand.entries.size)
    }

    @Test
    fun `每条指令的命令字都在官方集合内且无重复`() {
        val actual = SliderCommand.entries.map { it.opcode }
        assertEquals("存在重复命令字", actual.size, actual.toSet().size)
        assertEquals(officialOpcodes, actual.toSet())
    }

    @Test
    fun `指令帧结构为 A5 5A 04 CMD_H CMD_L AA`() {
        SliderCommand.entries.forEach { command ->
            val frame = command.frame
            assertEquals("${command.name} 帧长", 6, frame.size)
            assertEquals(0xA5, FrameCodec.u(frame[0]))
            assertEquals(0x5A, FrameCodec.u(frame[1]))
            assertEquals(0x04, FrameCodec.u(frame[2]))
            assertEquals(command.hi, FrameCodec.u(frame[3]))
            assertEquals(command.lo, FrameCodec.u(frame[4]))
            assertEquals(0xAA, FrameCodec.u(frame[5]))
        }
    }

    @Test
    fun `关键指令逐字节比对官方实现`() {
        // 取自 app-service.js 的原始字面量
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0x5A, 0x04, 0xE7.toByte(), 0xB6.toByte(), 0xAA.toByte()),
            SliderCommand.HANDSHAKE.frame,
        )
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0x5A, 0x04, 0xA9.toByte(), 0xB9.toByte(), 0xAA.toByte()),
            SliderCommand.QUERY_ALL.frame,
        )
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0x5A, 0x04, 0xFF.toByte(), 0xB9.toByte(), 0xAA.toByte()),
            SliderCommand.START.frame,
        )
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0x5A, 0x04, 0xB5.toByte(), 0xB9.toByte(), 0xAA.toByte()),
            SliderCommand.STOP.frame,
        )
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0x5A, 0x04, 0xD0.toByte(), 0xB6.toByte(), 0xAA.toByte()),
            SliderCommand.POWER_OFF.frame,
        )
    }

    // ---------------------------------------------------------------- 切帧

    @Test
    fun `单帧正常切出`() {
        val buffer = SliderCommand.START.frame
        val result = FrameCodec.extract(buffer)
        assertEquals(1, result.frames.size)
        assertArrayEquals(buffer, result.frames[0])
        assertEquals(0, result.remainder.size)
    }

    @Test
    fun `粘包时切出多帧`() {
        val buffer = SliderCommand.START.frame + SliderCommand.STOP.frame + SliderCommand.QUERY_ALL.frame
        val result = FrameCodec.extract(buffer)
        assertEquals(3, result.frames.size)
        assertEquals(0, result.remainder.size)
    }

    @Test
    fun `不完整帧保留在缓冲中而不是被丢弃`() {
        val frame = SliderCommand.HANDSHAKE.frame
        val partial = frame.copyOfRange(0, 3) // 只有 A5 5A 04
        val result = FrameCodec.extract(partial)
        assertEquals(0, result.frames.size)
        assertArrayEquals("残缺帧必须原样保留", partial, result.remainder)
    }

    @Test
    fun `分两次到达的帧能正确拼接`() {
        val frame = SliderCommand.QUERY_ALL.frame
        val first = FrameCodec.extract(frame.copyOfRange(0, 2))
        assertEquals(0, first.frames.size)

        val second = FrameCodec.extract(first.remainder + frame.copyOfRange(2, frame.size))
        assertEquals(1, second.frames.size)
        assertArrayEquals(frame, second.frames[0])
    }

    @Test
    fun `帧头前的噪声被跳过`() {
        val noise = byteArrayOf(0x00, 0x11, 0x22, 0xA5.toByte())
        val result = FrameCodec.extract(noise + SliderCommand.START.frame)
        assertEquals(1, result.frames.size)
        assertArrayEquals(SliderCommand.START.frame, result.frames[0])
    }

    @Test
    fun `帧尾非法时只前进一字节并重新同步`() {
        // 伪造一个帧头正确但帧尾错误的数据，紧随其后的是一帧合法数据
        val broken = byteArrayOf(0xA5.toByte(), 0x5A, 0x04, 0x11, 0x22, 0x00)
        val result = FrameCodec.extract(broken + SliderCommand.STOP.frame)
        assertEquals(1, result.frames.size)
        assertArrayEquals(SliderCommand.STOP.frame, result.frames[0])
    }

    @Test
    fun `长度字段越界被当作噪声`() {
        val weird = byteArrayOf(0xA5.toByte(), 0x5A, 0x00, 0x01, 0x02, 0xAA.toByte())
        val result = FrameCodec.extract(weird + SliderCommand.START.frame)
        assertEquals(1, result.frames.size)
        assertArrayEquals(SliderCommand.START.frame, result.frames[0])
    }

    @Test
    fun `完全不匹配的数据不会导致崩溃且全部保留`() {
        val garbage = ByteArray(32) { 0x5A }
        val result = FrameCodec.extract(garbage)
        assertEquals(0, result.frames.size)
        assertTrue(result.remainder.size <= garbage.size)
    }

    @Test
    fun `十六进制输出可用于诊断日志`() {
        assertEquals("A5 5A 04 FF B9 AA", FrameCodec.hex(SliderCommand.START.frame))
        assertEquals("A55A04FFB9AA", FrameCodec.hexCompact(SliderCommand.START.frame))
    }
}
