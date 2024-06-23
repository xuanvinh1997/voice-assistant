package ai.assistant

import ai.assistant.service.ASRService
import ai.assistant.service.LLMService
import ai.assistant.service.OverlayService
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
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val utteranceId = "utteranceId"
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
    private lateinit var asrService: ASRService
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

    private val asrConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ASRService.LocalBinder
            asrService = binder.getService()
            pipeline.addListener(asrService)
//            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
//            isBound = false
        }
    }
    private lateinit var llmService: LLMService
    private val llmConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LLMService.LocalBinder
            llmService = binder.getService()
//            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
//            isBound = false
        }
    }

    private lateinit var tts: TextToSpeech
    private val stopSentenceRegex = ".*[.!?,;]".toRegex()

    private val textToSpeechQueue = mutableListOf<String>()

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "1-messageReceiver")
            val message = intent?.getStringExtra("message")
            val lastMessage = messageAdapter.getMessage(messageAdapter.itemCount - 1)
            if(!lastMessage.isUser) {
                lastMessage.text += " $message"
                messageAdapter.notifyItemChanged(messageAdapter.itemCount - 1)
            } else {
                messageAdapter.addMessages(Message(message!!, false))
            }
//            messageAdapter.addMessages(Message(message!!, false))
            textToSpeechQueue.add(message!!)
            if (stopSentenceRegex.matches(message)) {
                tts.speak(textToSpeechQueue.joinToString(""), TextToSpeech.QUEUE_ADD, null, utteranceId)
                textToSpeechQueue.clear()
            }
        }
    }

    private val userMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "2-userMessageReceiver")
            val message = intent?.getStringExtra("message")
            messageAdapter.addMessages(Message(message!!, true))
//            llmService.runInference(message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ActivityCompat.requestPermissions(this, permissions, PackageManager.PERMISSION_GRANTED)

        // Initialize TextToSpeech and set the listener to 'this'
        tts = TextToSpeech(this, this, "ai.assistant.service.TtsService")
        // text to speech
        val ttsIntent = Intent(this, TtsService::class.java)
        bindService(ttsIntent, connection, Context.BIND_AUTO_CREATE)
        // wakeword
        val wakeWordIntent = Intent(this, WakeWordService::class.java)
        bindService(wakeWordIntent, wakeWordConnection, Context.BIND_AUTO_CREATE)
        // asr
        val asrIntent = Intent(this, ASRService::class.java)
        bindService(asrIntent, asrConnection, Context.BIND_AUTO_CREATE)
        // llm
        val llmIntent = Intent(this, LLMService::class.java)
        bindService(llmIntent, llmConnection, Context.BIND_AUTO_CREATE)

        // start pipeline
        pipeline = Pipeline(applicationContext)


        pipeline.startRecording()
        val filter = IntentFilter(Events.KWS_DETECTED)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
        val filter2 = IntentFilter(Events.ON_ASSISTANT_MESSAGE)
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, filter2)

        val filter3 = IntentFilter(Events.ON_USER_MESSAGE)
        LocalBroadcastManager.getInstance(this).registerReceiver(userMessageReceiver, filter3)

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
            messageAdapter.addMessages(Message("I'm here", false))
//            val value = intent.getStringExtra("key")
//            Log.d("WakeWordService", "Received event with value $value")
//            tts.speak("Chào bạn", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unbindService(wakeWordConnection)
        unbindService(asrConnection)
        unbindService(llmConnection)
//        stopService(asr)
        pipeline.stopRecording()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(java.util.Locale.US)
            setupUtteranceProgressListener()
            Log.d("TTS", "Initialization Success!")
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }
    private fun setupUtteranceProgressListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTS", "onStart utteranceId=$utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TTS", "onDone utteranceId=$utteranceId")
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTS", "onError utteranceId=$utteranceId")
                }
            })

            tts.setOnUtteranceCompletedListener { utteranceId ->
                Log.d("TTS", "onUtteranceCompleted utteranceId=$utteranceId")
                if (utteranceId == this@MainActivity.utteranceId){
                    Log.d("TTS", "Stop floating button service")
                    stopFloatingButtonService()
                }
            }
        } else {
            Log.e("TTS", "UtteranceProgressListener is not supported on this device.")
        }
    }
    private fun stopFloatingButtonService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }

    private fun startFloatingButtonService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
    }

}
