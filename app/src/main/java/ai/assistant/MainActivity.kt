package ai.assistant

import ai.assistant.service.IAssistantListener
import ai.assistant.service.RedirectService
import ai.assistant.service.TtsService
import ai.assistant.service.WakeWordService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity(), IAssistantListener, TextToSpeech.OnInitListener {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1
    private val REQUEST_RECORD_AUDIO_PERMISSION: Int = 200
    private lateinit var messageAdapter: MessageAdapter
    private var mTtsService: TtsService? = null
    //    private lateinit var messagesViewModel: MessagesViewModel
    private var isLoading = false
    private var isLastPage = false
    private lateinit var pipeline:Pipeline
    private val permissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.SYSTEM_ALERT_WINDOW
    )
    private var isBound = false
    private lateinit var wakeWordService: WakeWordService
    private lateinit var recyclerView: RecyclerView
    private var botIncomingMessage: MutableList<Message> = mutableListOf()
    private var messageList = ArrayList<Message>()
    private var textChunks = ArrayList<String>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TtsService.LocalBinder
            mTtsService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }
    private val wakeWordConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WakeWordService.LocalBinder
            wakeWordService = binder.getService()
            pipeline.addListener(wakeWordService)
//            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
//            isBound = false
        }
    }
    private lateinit var tts: TextToSpeech
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ActivityCompat.requestPermissions(this, permissions, PackageManager.PERMISSION_GRANTED)

        // Initialize TextToSpeech and set the listener to 'this'
        tts = TextToSpeech(this, this, "package ai.assistant.service.TtsService")
        val intent = Intent(this, TtsService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        val wakeWordService = Intent(this, WakeWordService::class.java)
        startService(wakeWordService)
        bindService(wakeWordService, wakeWordConnection, Context.BIND_AUTO_CREATE)
        // start pipeline
        pipeline = Pipeline(applicationContext)


        pipeline.startRecording()

        val filter = IntentFilter(Events.KWS_DETECTED)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
        recyclerView = findViewById(R.id.recyclerView)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter


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
    // Receiving Service
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Handle the event
//            val value = intent.getStringExtra("key")
//            Log.d("WakeWordService", "Received event with value $value")
            tts.speak("Chào bạn", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unbindService(wakeWordConnection)
        pipeline.stopRecording()
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
            val text = textChunks.joinToString(" ")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            textChunks.clear()
        }
//            val audio = TtsEngine.tts!!.generate(textChunks.joinToString(" "), TtsEngine.speakerId, TtsEngine.speed)
//            mediaPlayer?.stop()
//            val tmp = File.createTempFile("tmp", ".wav")
//            audio.save(tmp.path)
//            // Set the data source using a ByteArrayInputStream
//            mediaPlayer = android.media.MediaPlayer.create(this, android.net.Uri.fromFile(tmp))
//            mediaPlayer?.start()
//            textChunks.clear()

        // refresh the recycler view
        recyclerView.smoothScrollToPosition(0)
        recyclerView.adapter!!.notifyItemChanged(messageAdapter.itemCount-1)

    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("TTS", "Initialization Success!")
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }



}

