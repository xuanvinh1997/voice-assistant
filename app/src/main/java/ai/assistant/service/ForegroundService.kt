package ai.assistant.service

import ai.assistant.Events
import ai.assistant.MainActivity
import ai.assistant.Message
import ai.assistant.MessageAdapter
import ai.assistant.Pipeline
import ai.assistant.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import android.speech.tts.UtteranceProgressListener

class ForegroundService : Service(), TextToSpeech.OnInitListener  {

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

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.float_button)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        // Thực hiện công việc nền ở đây //
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

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ForegroundService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(java.util.Locale.US)
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
                if (utteranceId == this@ForegroundService.utteranceId){
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


    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Handle the event
            messageAdapter.addMessages(Message("I'm here", false))
//            val value = intent.getStringExtra("key")
//            Log.d("WakeWordService", "Received event with value $value")
//            tts.speak("Chào bạn", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
}
