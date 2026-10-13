package com.nuotaike.ntk1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuotaike.ntk1.ui.theme.DangerRed
import com.nuotaike.ntk1.ui.theme.SuccessGreen
import com.nuotaike.ntk1.ui.theme.WarningAmber
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 二级页面顶部的返回栏。 */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    // 注意：key 必须是稳定值。用 lambda 当 key 会导致每次重组都重建手势处理器，
    // 触摸过程中一旦重组，这次点击就被取消——真机上页面每秒都在刷新，必然点不动。
    val currentOnBack by rememberUpdatedState(onBack)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnBack() }) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "返回",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 统一的卡片容器：标题 + 可选副标题 + 内容。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** 指标块：小标签 + 大数值 + 单位。 */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String? = null,
    accent: Color? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}

/**
 * 按下即触发、长按连发的按钮。
 *
 * 用于滑轨的方向点动：单击 = 一次点动；按住 = 每 160ms 连发一次，
 * 松手立即停止连发（设备侧的停止仍由"停止"键兜底）。
 */
@Composable
fun HoldRepeatButton(
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDelayMs: Long = 400L,
    repeatEveryMs: Long = 160L,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentOnFire by rememberUpdatedState(onFire)
    val currentEnabled by rememberUpdatedState(enabled)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (currentEnabled) background else background.copy(alpha = 0.4f))
            .pointerInput(currentEnabled) {
                if (!currentEnabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        currentOnFire()
                        coroutineScope {
                            val repeat = launch {
                                delay(holdDelayMs)
                                while (isActive) {
                                    currentOnFire()
                                    delay(repeatEveryMs)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 按下触发、松手触发的按钮。
 *
 * 用于"微调模式"下的方向控制：**按住持续移动，松手立即停止**。
 * 这是真机验证出的正确用法——微调模式下设备接受的是
 * 「按住指令」+「松开指令」这一对，而不是普通模式下的单次点动。
 */
@Composable
fun PressReleaseButton(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentPress by rememberUpdatedState(onPress)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentEnabled by rememberUpdatedState(enabled)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (currentEnabled) background else background.copy(alpha = 0.4f))
            .pointerInput(currentEnabled) {
                if (!currentEnabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        currentPress()
                        tryAwaitRelease()
                        currentRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 圆形主控按钮（启动 / 停止）。 */@Composable
fun PrimaryCircleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier
                .size(104.dp)
                .clip(RoundedCornerShape(52.dp))
                .background(if (enabled) color else color.copy(alpha = 0.35f))
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures(onTap = { onClick() })
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 通用文字按钮块（带边框，热区 ≥ 48dp）。 */
@Composable
fun ActionTile(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    height: androidx.compose.ui.unit.Dp = 52.dp,
) {
    val bg = when {
        // 禁用态给描边而不是填充：保持"这里有个按钮"的形状可读性
        !enabled -> Color.Transparent
        selected -> accent.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = when {
        selected && enabled -> accent
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                selected -> accent
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
        )
    }
}

/**
 * 电量指示。
 *
 * 真机实测：NTK-1 控制器上报的电压在 **5.1V 左右**，
 * 不是 3S 锂电的 9–12.6V。在不知道电池配置的前提下，
 * 用任何固定量程去画"电量格数"都会误导用户（最早版本就把 5.1V 画成了红色空电）。
 *
 * 因此这里只做**忠实显示**：画出电池外形 + 原始电压，
 * 颜色只用于提示"电压低到不正常"，不假装知道剩余百分比。
 */
@Composable
fun BatteryGauge(
    volts: Double?,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        volts == null -> MaterialTheme.colorScheme.onSurfaceVariant
        // 低于 3V 对任何常见电池都算异常
        volts < 3.0 -> DangerRed
        volts < 6.0 -> WarningAmber
        else -> SuccessGreen
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 30.dp, height = 15.dp)) {
            val stroke = 1.6.dp.toPx()
            val bodyWidth = size.width - 4.dp.toPx()
            drawRoundRect(
                color = tint,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = stroke),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(bodyWidth + 1.dp.toPx(), size.height * 0.32f),
                size = Size(3.dp.toPx(), size.height * 0.36f),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            // 不画"剩余格数"：电池配置未知，画了就是编造
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = volts?.let { "%.1fV".format(it) } ?: "--.-V",
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}

/** 连接状态圆点 + 文案。 */
@Composable
fun ConnectionDot(
    connected: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        connected -> SuccessGreen
        busy -> WarningAmber
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color),
    )
}

/** 一条"标签 — 值"的横向信息行。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}
