package com.nuotaike.ntk1.protocol

/**
 * 帧编解码。
 *
 * 下行：`A5 5A 04 CMD_H CMD_L AA`，长度字段 `0x04` = 自长度字节起算到帧尾的字节数。
 * 上行：`A5 5A LEN CMD D1 D2 X AA`，其中 `D1 D2` 为 16 位大端数据。
 *
 * 关于第 7 字节：官方从未读取它，其语义（是否为校验和）无法从代码确认。
 * 本实现同样忽略该字节，仅校验帧尾 `AA`。
 */
object FrameCodec {

    const val HEADER_0 = 0xA5
    const val HEADER_1 = 0x5A
    const val TAIL = 0xAA
    const val TX_LENGTH_FIELD = 0x04
    const val TX_FRAME_SIZE = 6

    /** 长度字段的合法范围（防御性校验，避免被噪声误导而永久错位）。 */
    private const val MIN_LENGTH_FIELD = 3
    private const val MAX_LENGTH_FIELD = 64

    fun encode(cmd: SliderCommand): ByteArray = encode(cmd.hi, cmd.lo)

    fun encode(hi: Int, lo: Int): ByteArray = byteArrayOf(
        HEADER_0.toByte(),
        HEADER_1.toByte(),
        TX_LENGTH_FIELD.toByte(),
        hi.toByte(),
        lo.toByte(),
        TAIL.toByte(),
    )

    data class ExtractResult(
        /** 本次切出的完整帧，按到达顺序。 */
        val frames: List<ByteArray>,
        /** 尚未构成完整帧的剩余字节，需与后续数据拼接后重试。 */
        val remainder: ByteArray,
    )

    /**
     * 从接收缓冲中切出所有完整帧。
     *
     * 与官方实现的区别（修复原版缺陷）：
     * - 帧不完整时**保留**剩余字节等待后续数据，而不是直接丢弃或错位解析；
     * - 帧尾不合法时只丢弃 1 个字节重新同步，而不是整段跳过；
     * - 长度字段越界时视为噪声，同样只前进 1 字节。
     */
    fun extract(buffer: ByteArray): ExtractResult {
        val frames = ArrayList<ByteArray>(4)
        var i = 0
        val n = buffer.size

        while (i + 1 < n) {
            if (u(buffer[i]) != HEADER_0 || u(buffer[i + 1]) != HEADER_1) {
                i++
                continue
            }
            // 帧头已定位，但长度字节还没到 → 等待更多数据
            if (i + 2 >= n) break

            val lengthField = u(buffer[i + 2])
            if (lengthField < MIN_LENGTH_FIELD || lengthField > MAX_LENGTH_FIELD) {
                i++
                continue
            }

            val total = lengthField + 2
            // 整帧还没收全 → 保留，等下一批数据
            if (i + total > n) break

            if (u(buffer[i + total - 1]) != TAIL) {
                i++
                continue
            }

            frames += buffer.copyOfRange(i, i + total)
            i += total
        }

        return ExtractResult(frames, buffer.copyOfRange(i, n))
    }

    /** 上行帧的命令字（第 4 字节）。 */
    fun telemetryOpcode(frame: ByteArray): Int =
        if (frame.size > 3) u(frame[3]) else -1

    /** 上行帧的 16 位大端数据（第 5、6 字节）。 */
    fun telemetryValue(frame: ByteArray): Int =
        if (frame.size > 5) (u(frame[4]) shl 8) or u(frame[5]) else 0

    fun u(b: Byte): Int = b.toInt() and 0xFF

    fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(u(it)) }

    fun hexCompact(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(u(it)) }
}
