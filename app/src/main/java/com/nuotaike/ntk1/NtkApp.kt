package com.nuotaike.ntk1

import android.app.Application
import android.content.Context
import com.nuotaike.ntk1.ble.BleClient
import com.nuotaike.ntk1.domain.SliderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手工依赖容器。
 *
 * 刻意不使用 Hilt：本项目依赖图只有 2 个对象，手工装配更少构建风险、更快编译。
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bleClient = BleClient(context.applicationContext, appScope)

    val repository = SliderRepository(bleClient, appScope)
}

class NtkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
