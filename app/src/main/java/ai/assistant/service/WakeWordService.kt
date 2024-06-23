package ai.assistant.service

import ai.assistant.Events
import ai.assistant.SharedAudioRecorder
import ai.assistant.asr.WakeWord
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.Executors


class WakeWordService : Service(), SharedAudioRecorder.AudioDataListener {

    // local binder
    // Define the Binder object that the client will use to interact with the service
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1
    private var lastBroadcastTime = 0L
    private val broadcastInterval = 3000 // 30 seconds in milliseconds

    private lateinit var mWakeWord: WakeWord
    private var isDetected = false
    override fun onCreate() {
        super.onCreate()

        mWakeWord = WakeWord(this)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    private fun detectWakeWord(audioBuffer: ShortArray) {

        if (mWakeWord.invoke(audioBuffer)) {
            // start floating button service

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
//                    startActivityForResult(this, OVERLAY_PERMISSION_REQUEST_CODE)
                } else {
                    Log.d("wakeword", "Overlay permission already granted")
                    startFloatingButtonService()

                }
            } else {
                startFloatingButtonService()
            }

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBroadcastTime >= broadcastInterval) {
                lastBroadcastTime = currentTime

                val intent = Intent(Events.KWS_DETECTED)
                intent.putExtra("key", "value")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }

    }

    override fun onAudioData(data: ShortArray?, length: Int) {
        Executors.newSingleThreadExecutor().submit {
            detectWakeWord(data!!)
        }
    }

    private fun startFloatingButtonService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
    }
}