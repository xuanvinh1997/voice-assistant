package ai.assistant.tts

import android.app.Application
import android.content.Context
import android.os.Build


class App: Application() {
    private var storageContext: Context? = null
    override fun onCreate() {
        super.onCreate()
        storageContext = applicationContext.createDeviceProtectedStorageContext()
    }

    public fun getStorageContext(): Context {
        return storageContext!!
    }

    companion object {
        fun getStorageContext(): Context {
            return App().getStorageContext()
        }
    }

}
