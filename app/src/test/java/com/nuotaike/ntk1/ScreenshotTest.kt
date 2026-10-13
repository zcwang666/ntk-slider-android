package com.nuotaike.ntk1

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 界面截图（Robolectric 原生图形模式）。
 *
 * 不走 Compose 测试时钟（在 Robolectric 下会挂起），而是把 Activity 的 decorView
 * 手动测量、布局后直接绘制到 Bitmap。产物在 `app/build/screenshots/`。
 *
 * 「已连接」状态由 debug 专用的 [PreviewHostActivity] 提供样例数据渲染，
 * 因为测试环境不可能真的连上滑轨。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private val outputDir: File = File("build/screenshots").apply { mkdirs() }

    private val width = 1080

    /** 普通手机高度。 */
    private val phoneHeight = 2340

    /** 长截图高度：用于一次看完整页（含折叠线以下的内容）。 */
    private val longHeight = 3400

    private fun render(
        activityClass: Class<*>,
        intent: Intent,
        fileName: String,
        height: Int = phoneHeight,
    ) {
        @Suppress("UNCHECKED_CAST")
        val controller = Robolectric.buildActivity(
            activityClass as Class<android.app.Activity>,
            intent,
        ).setup()
        val activity = controller.get()

        val decor: View = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))

        val file = File(outputDir, "$fileName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        var nonBlank = 0
        for (y in 0 until height step 8) {
            for (x in 0 until width step 8) {
                if (bitmap.getPixel(x, y) != 0) nonBlank++
            }
        }
        assertTrue("$fileName 应绘制出实际内容", nonBlank > 100)
        assertTrue("$fileName 文件应已写出", file.length() > 1_000)

        controller.pause().stop().destroy()
    }

    private fun capturePreview(screen: String, fileName: String, height: Int = longHeight) {
        val intent = Intent(RuntimeEnvironment.getApplication(), PreviewHostActivity::class.java)
            .putExtra(PreviewHostActivity.EXTRA_SCREEN, screen)
        render(PreviewHostActivity::class.java, intent, fileName, height)
    }

    private fun captureMainTab(tab: Int, fileName: String) {
        val intent = Intent(RuntimeEnvironment.getApplication(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_INITIAL_TAB, tab)
        render(MainActivity::class.java, intent, fileName)
    }

    @Test
    fun `导出关键界面截图`() {
        // 已连接状态下的首页（底部工作模式条 + 右上角入口）
        capturePreview(PreviewHostActivity.SCREEN_REMOTE, "01-remote-connected")
        // 运行中：同一个按键切换成"停止"
        capturePreview(PreviewHostActivity.SCREEN_REMOTE_RUNNING, "02-remote-running")
        // 行程标定模式开启时的提示
        capturePreview(PreviewHostActivity.SCREEN_REMOTE_FINETUNE, "03-remote-finetune")
        // 延时模式：底部区域变成相片设置
        capturePreview(PreviewHostActivity.SCREEN_REMOTE_PHOTO, "04-remote-photo")
        // 设置（参数二级菜单）
        capturePreview(PreviewHostActivity.SCREEN_PARAMS, "05-settings")
        // 未连接状态的引导
        captureMainTab(MainActivity.TAB_REMOTE, "06-remote-disconnected")
        // 设备页
        captureMainTab(MainActivity.TAB_DEVICE, "07-device")
    }
}
