package com.nuotaike.ntk1.ble

/** 连接状态机。 */
enum class ConnectionState {
    /** 未连接，也没在扫描。 */
    IDLE,

    /** 正在扫描设备。 */
    SCANNING,

    /** 正在建立 GATT 连接。 */
    CONNECTING,

    /** 已连接，正在发现服务与特征。 */
    DISCOVERING,

    /** 就绪，可以收发指令。 */
    READY,

    /** 掉线后自动重连中。 */
    RECONNECTING,

    /** 连接失败。 */
    FAILED,
    ;

    val isBusy: Boolean get() = this == SCANNING || this == CONNECTING || this == DISCOVERING
    val isUsable: Boolean get() = this == READY
}

/** 扫描到的候选设备。 */
data class DiscoveredDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeen: Long,
    /** 设备名是否与 NTK-1 系列匹配（不匹配的仅作展示，默认不推荐连接）。 */
    val matchesTarget: Boolean,
)

/** 诊断用的一条链路事件。 */
data class LinkLog(
    val at: Long,
    val direction: Direction,
    val text: String,
) {
    enum class Direction { TX, RX, INFO, ERROR }
}

/** 设备名过滤规则，与官方小程序一致。 */
object DeviceFilter {
    private val EXACT = setOf("NTK-1", "NTK-1 AT+NAMENTK-1", "JDY-19")

    fun matches(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (EXACT.contains(name)) return true
        // 兼容带后缀的广播名，例如 "NTK-1-A3F2"
        return name.startsWith("NTK-1")
    }

    /** 用于界面提示的可读名称。 */
    fun displayName(name: String?): String = name?.takeIf { it.isNotBlank() } ?: "未知设备"
}
