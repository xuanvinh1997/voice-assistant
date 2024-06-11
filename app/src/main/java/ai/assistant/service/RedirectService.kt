package ai.assistant.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat


class RedirectService : Service() {

    private val REQUEST_CALL_PERMISSION = 1
    override fun onBind(intent: Intent?): IBinder? {
        return null // Không sử dụng Binder trong ví dụ này
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Lấy địa chỉ từ Intent (nếu có)
        val value = intent?.getStringExtra("value")
        val case = intent?.getStringExtra("case")
        // switch case kotlin
        when (case) {
            "map" -> {
                if (value != null) {
                    openGoogleMaps(value)
                } else {
                    // Thông báo nếu không có địa chỉ được cung cấp
                    Toast.makeText(this, "Không có địa chỉ để mở trong Google Maps", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            "call" -> {
                makePhoneCall(value.toString())
            }
            "media" -> {
                openMediaGallery()
            }
            "youtube" -> {
                openYouTubeWithSearchQuery(value.toString())
            }
            else -> {
                // Thông báo nếu không có case nào được cung cấp
                Toast.makeText(this, "Không có case được cung cấp", Toast.LENGTH_SHORT).show()
            }
        }

        // Dừng service sau khi thực hiện xong
        stopSelf()
        return START_NOT_STICKY
    }

    private fun openGoogleMaps(address: String) {
        val mapIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Cần thiết để mở Activity từ Service
            }
        startActivity(mapIntent)
    }

    private fun makePhoneCall(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            //Creating intents for making a call
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.setData(Uri.parse("tel:$phoneNumber"))
            startActivity(callIntent)
        } else {
            Toast.makeText(this, "You don't assign permission.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMediaGallery() {
        // Tạo một Intent để mở ứng dụng bộ sưu tập
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "vnd.android.cursor.dir/image" // Thay "image" bằng "video" hoặc "audio" nếu bạn muốn mở bộ sưu tập video hoặc audio
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        // Kiểm tra xem có ứng dụng nào có thể xử lý Intent này hay không
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val isIntentSafe = activities.isNotEmpty()

        // Nếu có ít nhất một ứng dụng có thể xử lý Intent này, mở ứng dụng bộ sưu tập
        if (isIntentSafe) {
            startActivity(intent)
        } else {
            // Nếu không tìm thấy ứng dụng nào để mở bộ sưu tập, hiển thị thông báo cho người dùng
            Toast.makeText(this, "Không tìm thấy ứng dụng bộ sưu tập trên thiết bị của bạn.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openYouTubeWithSearchQuery(searchQuery: String) {
        val intent = Intent(Intent.ACTION_SEARCH)
        intent.setPackage("com.google.android.youtube")
        intent.putExtra("query", searchQuery)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}