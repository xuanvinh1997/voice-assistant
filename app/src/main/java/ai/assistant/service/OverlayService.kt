package ai.assistant.service

import ai.assistant.R
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.bumptech.glide.Glide

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var appButton: ImageView
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

//        appButton = ImageView(this)
//        appButton.setImageResource(R.drawable.float_button)
//        // set size of button
//        val buttonWidth = 150 // Chiều rộng cố định, đơn vị là pixel
//        val buttonHeight = 150 // Chiều cao cố định, đơn vị là pixel
//
//        params = WindowManager.LayoutParams(
//            buttonWidth,
//            buttonHeight,
//            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
//            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//            PixelFormat.TRANSLUCENT
//        )

        appButton = ImageView(this)
        Glide.with(this)
            .asGif()
            .load(R.drawable.float_button)
            .into(appButton)

        // Thiết lập kích thước của nút nổi
        val buttonSize = 150 // Kích thước mong muốn (pixels)

        params = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = 0
        params.y = 100

        // Xử lý sự kiện kéo nút nổi
        appButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(appButton, params)
                        return true
                    }
                }
                return false
            }
        })

        // Xử lý sự kiện click nút nổi để tắt nó
        appButton.setOnClickListener {
            stopSelf() // Dừng Service và gọi onDestroy
        }

        windowManager.addView(appButton, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appButton.isInitialized) {
            windowManager.removeView(appButton)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}