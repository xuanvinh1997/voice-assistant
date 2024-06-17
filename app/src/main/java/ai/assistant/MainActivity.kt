package ai.assistant

import ai.assistant.service.ASR
import ai.assistant.service.IAssistantListener
import ai.assistant.service.RedirectService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.k2fsa.sherpa.onnx.tts.engine.TtsEngine
import java.io.File


class MainActivity : AppCompatActivity(), IAssistantListener {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1
    private val REQUEST_RECORD_AUDIO_PERMISSION: Int = 200
    private lateinit var messageAdapter: MessageAdapter

    //    private lateinit var messagesViewModel: MessagesViewModel
    private var isLoading = false
    private var isLastPage = false
    private val permissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.SYSTEM_ALERT_WINDOW
    )

    private var mediaPlayer: android.media.MediaPlayer? = null
    private lateinit var asr: ASR
    private lateinit var recyclerView: RecyclerView
    private var botIncomingMessage: MutableList<Message> = mutableListOf()
    private var messageList = ArrayList<Message>()
    private var textChunks = ArrayList<String>()
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ActivityCompat.requestPermissions(this, permissions, PackageManager.PERMISSION_GRANTED)
        if (TtsEngine.tts == null) {
            TtsEngine.createTts(applicationContext)
        }

        asr = ASR(this)
        asr.start()
        asr.setAssistantListener(this)


//
//        // click button map
//        findViewById<android.widget.Button>(R.id.use_go_to_map_button).setOnClickListener {
//            openRedirectService("Vincom Nguyễn Chí Thanh", "map")
//        }
//
//        // click button media
//        findViewById<android.widget.Button>(R.id.media_button).setOnClickListener {
//            openRedirectService("", "media")
//        }
//
//        // click button youtube
//        findViewById<android.widget.Button>(R.id.youtube_button).setOnClickListener {
//            openRedirectService("Học lập trình Android", "youtube")
//        }
//
//        // click button call
//        findViewById<android.widget.Button>(R.id.call_button).setOnClickListener {
//            openRedirectService("0966913714", "call")
//        }

        recyclerView = findViewById(R.id.recyclerView)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter


//        recyclerView.addOnScrollListener(object : InfiniteScrollListener(layoutManager) {
//            override fun loadMoreItems() {
//                isLoading = true
//            }
//
//            override fun isLastPage(): Boolean = isLastPage
//
//            override fun isLoading(): Boolean = isLoading
//        })
//
//        messagesViewModel.messages.observe(this) { newMessages ->
//            messageAdapter.addMessages(newMessages)
//            isLoading = false
//            // Check if it's the last page and update isLastPage accordingly
//        }
//
//        // Initial load
//        messagesViewModel.loadMoreMessages()

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
//                    val intent = Intent(this, ASRService::class.java)
//                    startService(intent)

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

    private fun openRedirectService(value: String, case: String) {
        val serviceIntent = Intent(this, RedirectService::class.java).apply {
            putExtra("value", value)
            putExtra("case", case)
        }
        startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        asr.stop()
    }
    val regex = Regex("""(\.\.\.|[.?!])""")
    override fun onNewMessageSent(message: Message) {
        Log.d(TAG, "onNewMessageSent: ${message.text} ${message.isUser}")
        if (message.isUser)
            messageAdapter.addMessages(message)
        else {
            textChunks.add(message.text)
            val lastMessage = messageAdapter.getMessage(messageAdapter.itemCount - 1)
            if (!lastMessage.isUser) {
                lastMessage.text += message.text
            }else {
                messageAdapter.addMessages(message)
            }
        }
        if (message.text.contains(regex)) {
            val audio = TtsEngine.tts!!.generate(textChunks.joinToString(" "), TtsEngine.speakerId, TtsEngine.speed)
            mediaPlayer?.stop()
            val tmp = File.createTempFile("tmp", ".wav")
            audio.save(tmp.path)
            mediaPlayer = android.media.MediaPlayer.create(this, android.net.Uri.fromFile(tmp))
            mediaPlayer?.start()
            textChunks.clear()

        }
        // refresh the recycler view
        recyclerView.smoothScrollToPosition(0)
        recyclerView.adapter!!.notifyItemChanged(messageAdapter.itemCount-1)

    }
}

