package com.nuotaike.ntk1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nuotaike.ntk1.ble.ConnectionState
import com.nuotaike.ntk1.ble.DiscoveredDevice
import com.nuotaike.ntk1.ble.LinkLog
import com.nuotaike.ntk1.protocol.SliderCommand
import com.nuotaike.ntk1.protocol.SliderLanguage
import com.nuotaike.ntk1.protocol.SliderState
import com.nuotaike.ntk1.ui.components.ActionTile
import com.nuotaike.ntk1.ui.components.BackHeader
import com.nuotaike.ntk1.ui.components.InfoRow
import com.nuotaike.ntk1.ui.components.SectionCard
import com.nuotaike.ntk1.ui.theme.DangerRed
import com.nuotaike.ntk1.ui.theme.LiveCyan
import com.nuotaike.ntk1.ui.theme.SuccessGreen
import com.nuotaike.ntk1.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceScreen(
    connection: ConnectionState,
    connectedName: String?,
    state: SliderState,
    devices: List<DiscoveredDevice>,
    logs: List<LinkLog>,
    bluetoothSupported: Boolean,
    bluetoothEnabled: Boolean,
    hasPermissions: Boolean,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onResetLink: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onCommand: (SliderCommand) -> Unit,
    onSendRaw: (String) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(title = "设备", subtitle = "DEVICE", onBack = onBack)

        // ---------- 权限与蓝牙 ----------
        SectionCard(title = "连接条件", subtitle = "PREREQUISITES") {
            InfoRow(
                label = "蓝牙硬件",
                value = if (bluetoothSupported) "可用" else "不支持",
                valueColor = if (bluetoothSupported) SuccessGreen else DangerRed,
            )
            Spacer(Modifier.height(6.dp))
            InfoRow(
                label = "蓝牙开关",
                value = if (bluetoothEnabled) "已开启" else "未开启",
                valueColor = if (bluetoothEnabled) SuccessGreen else WarningAmber,
            )
            Spacer(Modifier.height(6.dp))
            InfoRow(
                label = "定位/蓝牙权限",
                value = if (hasPermissions) "已授权" else "未授权",
                valueColor = if (hasPermissions) SuccessGreen else WarningAmber,
            )

            if (!bluetoothEnabled || !hasPermissions) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasPermissions) {
                        ActionTile(
                            text = "授予权限",
                            onClick = onRequestPermissions,
                            modifier = Modifier.weight(1f),
                            accent = WarningAmber,
                            selected = true,
                        )
                    }
                    if (!bluetoothEnabled) {
                        ActionTile(
                            text = "开启蓝牙",
                            onClick = onEnableBluetooth,
                            modifier = Modifier.weight(1f),
                            accent = WarningAmber,
                            selected = true,
                        )
                    }
                }
            }
        }

        // ---------- 已连接设备 ----------
        if (connection.isUsable) {
            SectionCard(title = connectedName ?: "滑轨", subtitle = "CONNECTED") {
                InfoRow(label = "连接状态", value = connectionLabel(connection), valueColor = SuccessGreen)
                Spacer(Modifier.height(6.dp))
                InfoRow(label = "固件版本", value = state.firmwareLabel)
                Spacer(Modifier.height(6.dp))
                InfoRow(label = "轨道型号标定", value = state.railModel?.toString() ?: "--")
                Spacer(Modifier.height(6.dp))
                InfoRow(
                    label = "设备语言",
                    value = when (state.language) {
                        SliderLanguage.CHINESE -> "中文"
                        SliderLanguage.ENGLISH -> "English"
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(
                        text = "刷新状态",
                        onClick = { onCommand(SliderCommand.QUERY_ALL) },
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        text = if (state.language == SliderLanguage.CHINESE) "Switch to EN" else "切换为中文",
                        onClick = { onCommand(SliderCommand.LANGUAGE_TOGGLE) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                ActionTile(
                    text = "断开连接",
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    accent = DangerRed,
                )
            }
        }

        // ---------- 扫描 ----------
        SectionCard(title = "附近的滑轨", subtitle = "SCAN") {
            // 扫描与连接互斥：连上之后再扫描会干扰 GATT 链路，真机上会导致滑轨掉线且卡死
            val scanning = connection == ConnectionState.SCANNING
            val busy = connection.isUsable || connection == ConnectionState.RECONNECTING
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    text = if (scanning) "停止扫描" else "开始扫描",
                    onClick = {
                        if (scanning) onStopScan() else onScan()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = scanning || !busy,
                    selected = scanning,
                )
                ActionTile(
                    text = "复位链路",
                    onClick = onResetLink,
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    accent = WarningAmber,
                )
            }
            if (busy && !scanning) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已连接滑轨，扫描已禁用（扫描会干扰当前链路）。需要重新搜索请先断开连接，或点「复位链路」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    text = when (connection) {
                        ConnectionState.SCANNING -> "扫描中…请确保滑轨已上电"
                        else -> "尚未扫描。点击上方按钮搜索 NTK-1 / JDY-19。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        connected = connection.isUsable && connectedName == device.name,
                        enabled = !connection.isUsable,
                        onConnect = { onConnect(device.address) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ---------- 外观 ----------
        SectionCard(title = "显示", subtitle = "APPEARANCE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "深色主题",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "现场拍摄建议使用深色，强光环境下可切换浅色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() })
            }
        }

        // ---------- 原始指令（联调探测） ----------
        RawCommandCard(
            enabled = connection.isUsable,
            onSendRaw = onSendRaw,
        )

        // ---------- 链路日志 ----------
        SectionCard(
            title = "链路日志",
            subtitle = "共 ${logs.size} 条",
        ) {
            Text(
                text = "用于排查连接问题。TX = 下发指令，RX = 设备上报。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (logs.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                logs.takeLast(60).reversed().forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = timeFormat.format(Date(entry.at)),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            text = when (entry.direction) {
                                LinkLog.Direction.TX -> "TX"
                                LinkLog.Direction.RX -> "RX"
                                LinkLog.Direction.INFO -> "··"
                                LinkLog.Direction.ERROR -> "!!"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = when (entry.direction) {
                                LinkLog.Direction.TX -> LiveCyan
                                LinkLog.Direction.RX -> SuccessGreen
                                LinkLog.Direction.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                LinkLog.Direction.ERROR -> DangerRed
                            },
                            modifier = Modifier.width(26.dp),
                        )
                        Text(
                            text = entry.text,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDevice,
    connected: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (device.matchesTarget) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SuccessGreen.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "NTK-1",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${device.address}　${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionTile(
            text = if (connected) "已连接" else "连接",
            onClick = onConnect,
            modifier = Modifier.width(88.dp),
            enabled = enabled && !connected,
            height = 44.dp,
        )
    }
}

/**
 * 原始指令下发（联调探测）。
 *
 * 用途：官方小程序的 78 条指令可能不是固件的全部能力。
 * 联调时可以直接发任意字节，观察设备是否有反应，
 * 用来确认"速度设定""多点位"这类官方未暴露的指令是否存在。
 */
@Composable
private fun RawCommandCard(
    enabled: Boolean,
    onSendRaw: (String) -> Boolean,
) {
    var input by remember { mutableStateOf("A55A0422BAAA") }
    var feedback by remember { mutableStateOf<String?>(null) }

    SectionCard(title = "原始指令", subtitle = "联调探测") {
        Text(
            text = "直接下发 6 字节帧（十六进制，可带空格）。用于探测固件里可能存在、" +
                "但官方小程序未暴露的指令。发送结果会出现在下方链路日志里。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("帧内容") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile(
                text = "发送",
                onClick = {
                    feedback = if (onSendRaw(input)) {
                        "已加入发送队列"
                    } else {
                        "格式错误：需要偶数个十六进制字符"
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
                selected = true,
            )
            ActionTile(
                text = "查询状态",
                onClick = { input = "A55A04A9B9AA" },
                modifier = Modifier.weight(1f),
                enabled = true,
                height = 52.dp,
            )
        }

        feedback?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
