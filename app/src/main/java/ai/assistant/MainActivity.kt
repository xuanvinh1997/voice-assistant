package ai.assistant

import ai.assistant.service.ASRService
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1
    private val REQUEST_RECORD_AUDIO_PERMISSION:Int = 200
    private val permissions = arrayOf(android.Manifest.permission.RECORD_AUDIO)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this, permissions, OVERLAY_PERMISSION_REQUEST_CODE
                    )

                    // Permission granted, you can start your service here if needed
                    val intent = Intent(this, ASRService::class.java)
                    startService(intent)

                } else {
                    // Permission denied, handle accordingly

                }
            }

            OVERLAY_PERMISSION_REQUEST_CODE -> {

            }
        }
    }

    companion object {
        val TAG: String? = MainActivity::class.simpleName
    }

}

