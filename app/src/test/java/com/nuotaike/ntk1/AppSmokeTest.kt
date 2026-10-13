package com.nuotaike.ntk1

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 启动冒烟测试（Robolectric，无需真机）。
 *
 * 覆盖新的界面结构：首页 + 底部工作模式条 + 右上角设置/设备入口 + 参数二级菜单。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `应用能启动并渲染遥控主页`() {
        composeRule.onNodeWithText("未连接滑轨").assertIsDisplayed()
        composeRule.onNodeWithText("位置").assertIsDisplayed()
        composeRule.onNodeWithText("运行控制").assertExists()
        // 默认是视频模式，底部区域显示定时器
        composeRule.onNodeWithText("定时器").assertExists()
    }

    @Test
    fun `工作模式在底部常驻条里`() {
        // 中文标签里"延时"与位置卡的"延时"统计重名，所以用英文副标签做唯一断言
        composeRule.onNodeWithText("视频").assertIsDisplayed()
        composeRule.onNodeWithText("定格").assertIsDisplayed()
        composeRule.onNodeWithText("外触").assertIsDisplayed()
        listOf("VIDEO", "LAPSE", "ANIMA", "TRIG").forEach { en ->
            composeRule.onNodeWithText(en).assertIsDisplayed()
        }
        // 首页里不再有独立的"工作模式"卡片
        composeRule.onNodeWithText("工作模式").assertDoesNotExist()
    }

    @Test
    fun `启动与停止在同一个按键上切换`() {
        // 未运行时显示"启动"，且不再有第二个"■ 停止"按钮
        composeRule.onNodeWithText("启动").assertExists()
        composeRule.onNodeWithText("■ 停止").assertDoesNotExist()
    }

    @Test
    fun `速度按键在快门左右两侧`() {
        composeRule.onNodeWithText("运行控制").performScrollTo()
        composeRule.onNodeWithText("速度 −").assertExists()
        composeRule.onNodeWithText("快门").assertExists()
        composeRule.onNodeWithText("速度 ＋").assertExists()
        // 旧的"上升/下降"命名已不再出现
        composeRule.onNodeWithText("▲ 上升").assertDoesNotExist()
        composeRule.onNodeWithText("▼ 下降").assertDoesNotExist()
    }

    @Test
    fun `行程标定开关在运行控制里`() {
        composeRule.onNodeWithText("行程标定").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("方向控制需要先打开它").assertExists()
    }

    @Test
    fun `视频模式底部有往返开关`() {
        // 默认视频模式 → 底部是定时器卡，往返开关在最上面
        // （"往返"同时也是位置卡的统计标签，所以用唯一的状态文案断言）
        composeRule.onNodeWithText("已关闭").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("开启").assertExists()
    }

    @Test
    fun `定时器有开关按键且不再有预览定时`() {
        composeRule.onNodeWithText("开启定时器").performScrollTo().assertIsDisplayed()
        // 预览定时已撤：设备停止时不回传真实时长，查回来的是旧值，反而误导
        composeRule.onNodeWithText("预览定时").assertDoesNotExist()
    }

    @Test
    fun `已移除打点与场景功能`() {
        composeRule.onNodeWithText("AB 打点").assertDoesNotExist()
        composeRule.onNodeWithText("场景预设").assertDoesNotExist()
    }

    @Test
    fun `位置不显示毫米数字`() {
        composeRule.onNodeWithText("位置").assertIsDisplayed()
        composeRule.onNodeWithText("mm", substring = true).assertDoesNotExist()
    }

    @Test
    fun `右上角可以进入设置`() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("参数设置").assertIsDisplayed()
        // 「相片设置」「定时器」已移到首页，设置页只保留这三类
        listOf("运动参数", "行程与型号", "设备维护").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithText("相片设置").assertDoesNotExist()
        composeRule.onNodeWithText("定时器").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("运行控制").assertExists()
    }

    @Test
    fun `右上角可以进入设备页`() {
        composeRule.onNodeWithContentDescription("设备").performClick()
        composeRule.onNodeWithText("连接条件").assertIsDisplayed()
        composeRule.onNodeWithText("附近的滑轨").assertExists()
        composeRule.onNodeWithText("原始指令").assertExists()
        composeRule.onNodeWithText("未授权").assertExists()
        // 扫描卡死时的自救入口
        composeRule.onNodeWithText("复位链路").assertExists()
    }

    @Test
    fun `参数二级菜单能进入并返回`() {
        composeRule.onNodeWithContentDescription("设置").performClick()

        composeRule.onNodeWithText("行程与型号").performScrollTo().performClick()
        composeRule.onNodeWithText("行程档位").assertIsDisplayed()
        listOf("120", "220", "320", "350", "550", "750", "950", "1250", "370", "570", "770", "970")
            .forEach { composeRule.onNodeWithText(it).assertExists() }

        // 二级页面返回一级
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("参数设置").assertIsDisplayed()
    }
}
