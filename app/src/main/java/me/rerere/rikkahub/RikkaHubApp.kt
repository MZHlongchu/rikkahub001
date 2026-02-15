package me.rerere.rikkahub

import android.app.Application
import android.util.Log
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
// import com.google.firebase.remoteconfig.FirebaseRemoteConfig
// import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.data.container.PRootManager
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.service.RikkaHubForegroundService
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.utils.DatabaseUtil
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

private const val TAG = "RikkaHubApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"

class RikkaHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@RikkaHubApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
        }
        this.createNotificationChannel()

        // set cursor window size to 32MB
        DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)

        // delete temp files
        deleteTempFiles()

        // 恢复容器状态（如果之前已初始化）
        restoreContainerState()

        // 启动前台服务以保持后台运行
        RikkaHubForegroundService.startService(this)

        // Init remote config (Disabled)
        // get<FirebaseRemoteConfig>().apply {
        //     setConfigSettingsAsync(remoteConfigSettings {
        //         minimumFetchIntervalInSeconds = 1800
        //     })
        //     setDefaultsAsync(R.xml.remote_config_defaults)
        //     fetchAndActivate()
        // }

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    /**
     * App 启动时恢复容器状态
     * 解决重启后容器状态丢失的问题
     */
    private fun restoreContainerState() {
        val appScope = get<AppScope>()
        val prootManager = get<PRootManager>()

        appScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "[AppStartup] Checking container initialization status...")

                // 检查是否需要恢复（rootfs 是否已初始化）
                if (prootManager.checkInitializationStatus()) {
                    Log.d(TAG, "[AppStartup] Container rootfs found, restoring state...")

                    // 恢复容器状态
                    val restored = prootManager.restoreState()
                    if (restored) {
                        Log.d(TAG, "[AppStartup] Container state restored successfully")
                    } else {
                        Log.w(TAG, "[AppStartup] Failed to restore container state")
                    }
                } else {
                    Log.d(TAG, "[AppStartup] Container not initialized, skipping state restore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[AppStartup] Error restoring container state", e)
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "AppScope exception", e)
        }
)
