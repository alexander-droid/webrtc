package com.example.dev.webrtcclient.call.ptt

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.widget.Toast
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.GroupCallInfo
import com.example.dev.webrtcclient.model.GroupCallState
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

class PTTCallService : Service(), PTTWebRTCManager.Callback {

    private lateinit var audioManager: AudioManager

    private lateinit var focusRequest: AudioFocusRequest

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        var typeOfChange = "AUDIOFOCUS_NOT_DEFINED"
        typeOfChange = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
            AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
            else -> "AUDIOFOCUS_INVALID"
        }
        Log.e(TAG, "onAudioFocusChange: $typeOfChange")
    }

    private var callInfo: GroupCallInfo? = null

    private lateinit var webRTCManager: PTTWebRTCManager

    private val disposable = CompositeDisposable()

    val recipientsObservable: Observable<List<CallUserInfo>>
        get() = webRTCManager.recipientsObservable

    val recipientAddedObservable: Observable<CallUserInfo>
        get() = webRTCManager.recipientAddedObservable

    val recipientRemovedObservable: Observable<CallUserInfo>
        get() = webRTCManager.recipientRemovedObservable

    val callStateObservable: Observable<GroupCallState>
        get() = webRTCManager.callStateObservable

    val messageObservable: Observable<String>
        get() = webRTCManager.messageObservable

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                build()
            }
            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        webRTCManager = PTTWebRTCManager(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
        webRTCManager.release()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest)
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG,"onStartCommand ${intent?.action}")

        when(intent?.action) {
            ACTION_JOIN_GROUP -> {
                retrieveExtras(intent)
                webRTCManager.connect(callInfo)
                PTTCallActivity.start(this)
            }
            ACTION_LEAVE -> {
                onLeave("You left the group")
            }
        }
        return START_NOT_STICKY
    }

    private fun retrieveExtras(intent: Intent?) {
        intent?:return
        val channelName = intent.getStringExtra(EXTRA_GROUP_CHANNEL_NAME)
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE)
        val myName = intent.getStringExtra(EXTRA_MY_NAME)

        callInfo = GroupCallInfo(
            callType = callType,
            me = CallUserInfo(
                id = myName,
                name = myName
            ),
            channelName = channelName
        )

        Log.d(TAG,"EXTRA_CALL_TYPE $callType")
        Log.d(TAG,"EXTRA_MY_NAME $myName")
        Log.d(TAG,"EXTRA_GROUP_CHANNEL_NAME $channelName")
    }






    //User interaction
    fun leave() {
        startService(getLeaveIntent(this))
    }

    fun startTalking() {
        webRTCManager.startTalking()
    }

    fun stopTalking() {
        webRTCManager.stopTalking()
    }




    //WebRTC callbacks
    override fun onLeave(message: String?) {
        Handler(Looper.getMainLooper()).post {
            message?.also {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
            stopSelf()
        }
    }





    private val binder = CallBinder()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class CallBinder : Binder() {
        fun getService(): PTTCallService = this@PTTCallService
    }

    companion object {

        private const val TAG = "PTTCallService"

        const val ACTION_JOIN_GROUP = "ACTION_PERFORM_CALL"
        const val ACTION_LEAVE = "ACTION_LEAVE"

        const val EXTRA_GROUP_CHANNEL_NAME = "EXTRA_GROUP_CHANNEL_NAME"
        const val EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE"
        const val EXTRA_MY_NAME = "EXTRA_MY_NAME"

        const val CALL_TYPE_AUDIO_GROUP = "audio"

        fun startGroup(context: Context?, myName: String, groupChannelName: String) {
            context?.startService(Intent(context, PTTCallService::class.java).apply {
                action = ACTION_JOIN_GROUP
                putExtra(EXTRA_GROUP_CHANNEL_NAME, "presence-$groupChannelName")
                putExtra(EXTRA_CALL_TYPE, CALL_TYPE_AUDIO_GROUP)
                putExtra(EXTRA_MY_NAME, myName)
            })
        }

        fun getLeaveIntent(context: Context): Intent {
            return Intent(context, PTTCallService::class.java).apply {
                action = ACTION_LEAVE
            }
        }
    }
}
