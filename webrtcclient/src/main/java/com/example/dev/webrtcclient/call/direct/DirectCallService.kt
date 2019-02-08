package com.example.dev.webrtcclient.call.direct

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.dev.webrtcclient.BaseCallService
import com.example.dev.webrtcclient.call.direct.DirectWebRTCManager.Companion.CALL_TYPE_VIDEO
import com.example.dev.webrtcclient.call.direct.DirectWebRTCManager.Companion.CALL_TYPE_VOICE
import com.example.dev.webrtcclient.call.direct.ui.DirectCallActivity
import com.example.dev.webrtcclient.log.SimpleEvent
import com.example.dev.webrtcclient.model.CallInfo
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.CallViewState
import com.example.dev.webrtcclient.model.DirectCallState
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.lang.reflect.Array.getLength
import android.content.res.AssetFileDescriptor
import com.example.dev.webrtcclient.R
import java.io.IOException


class DirectCallService : BaseCallService(), DirectWebRTCManager.Callback {

    private val callBeepHandler = Handler(Looper.getMainLooper())
    private val callVibrationHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockCPU: PowerManager.WakeLock? = null

    private val disposable = CompositeDisposable()

    private val userInfoSubject = BehaviorSubject.create<CallUserInfo>()
    val userInfoObservable: Observable<CallUserInfo>
        get() = userInfoSubject

    val callStateObservable: Observable<DirectCallState>
        get() = webRTCManager.callStateObservable

    val loglist: MutableList<SimpleEvent>
        get() = webRTCManager.logList
    val logObservable: Observable<SimpleEvent>
        get() = webRTCManager.logObservable

    val callViewStateObservable: Observable<CallViewState>
        get() = webRTCManager.callStateObservable
            .map {
                return@map when(it) {
                    DirectCallState.NONE -> CallViewState.NONE
                    DirectCallState.INITIALIZING_CALLING_IN,
                    DirectCallState.CALLING_IN -> {
                        CallViewState.CALLING_IN
                    }
                    DirectCallState.INITIALIZING_CALLING_OUT,
                    DirectCallState.CALLING_OUT,
                    DirectCallState.CREATING_OFFER,
                    DirectCallState.AWAITING_ANSWER,
                    DirectCallState.SETTING_ANSWER -> {
                        CallViewState.CALLING_OUT
                    }
                    DirectCallState.AWAITING_OFFER,
                    DirectCallState.SETTING_OFFER,
                    DirectCallState.CREATING_ANSWER,
                    DirectCallState.CALL_RUNNING -> CallViewState.CALL_RUNNING
                }
            }
            .distinctUntilChanged()

    private lateinit var callNotificationHelper: DirectCallNotificationHelper

    private lateinit var webRTCManager: DirectWebRTCManager

    private val binder = CallBinder()

    private lateinit var callInfo: CallInfo

    val rootEglBaseObserver: Observable<EglBase>
        get() = webRTCManager.rootEglBaseObserver

    val localVideoTrackObserver: Observable<VideoTrack>
        get() = webRTCManager.localVideoTrackObserver

    val remoteVideoTrackObserver: Observable<VideoTrack>
        get() = webRTCManager.remoteVideoTrackObserver


    private var isFirstLaunch = true

    private var incomingCallPlayer: MediaPlayer? = null
    private lateinit var toneGenerator: ToneGenerator
    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "onCreate")

        callNotificationHelper = DirectCallNotificationHelper(this)
        webRTCManager = DirectWebRTCManager(this, this)

        disposable.add(callStateObservable.subscribe {
            applyState(it)
        })


        try {
            incomingCallPlayer = MediaPlayer()
            incomingCallPlayer?.setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
            incomingCallPlayer?.setDataSource(this, Settings.System.DEFAULT_RINGTONE_URI)
            incomingCallPlayer?.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG,"onDestroy")
        disposable.dispose()
        webRTCManager.release()

        wakeLock?.release()
        wakeLockCPU?.release()

        stopIncomingCallSound()
        stopVibrate()
        stopBeeping()

        toneGenerator.release()
        incomingCallPlayer?.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG,"onStartCommand ${intent?.action}")

        if (isFirstLaunch) {
            retrieveExtras(intent)
            isFirstLaunch = false
        }

        when(intent?.action) {
            ACTION_PERFORM_CALL -> {
                webRTCManager.doCall(callInfo)
            }
            ACTION_RECEIVE_CALL -> {
                webRTCManager.callReceived(callInfo)
            }
            ACTION_ANSWER_CALL -> {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                webRTCManager.answerCall()
            }
            ACTION_DECLINE_CALL -> {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                webRTCManager.declineCall()
            }
        }

        return START_NOT_STICKY
    }

    private fun startCallActivity() {
        when(callInfo.callType) {
            CALL_TYPE_VIDEO -> {
                DirectCallActivity.startVideo(this)
            }
            CALL_TYPE_VOICE -> {
                DirectCallActivity.startVoice(this)
            }
        }
    }

    private fun beep() {
        callBeepHandler.postDelayed({
            toneGenerator.startTone(ToneGenerator.TONE_SUP_DIAL, 1500)
            beep()
        }, 5000)
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(1000)
        }
//        callVibrationHandler.postDelayed({vibrate()}, 5000)
    }

    private fun startIncomingCallSound() {
        incomingCallPlayer?.start()
    }

    private fun stopVibrate() {
        callVibrationHandler.removeCallbacksAndMessages(null)
        vibrator.cancel()
    }

    private fun stopIncomingCallSound() {
        incomingCallPlayer?.stop()
    }

    private fun stopBeeping() {
        callBeepHandler.removeCallbacksAndMessages(null)
        toneGenerator.stopTone()
    }



    private fun retrieveExtras(intent: Intent?) {
        intent?:return
        val presenceChannelName = intent.getStringExtra(EXTRA_PRESENCE_CHANNEL_NAME)
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE)
        val myName = intent.getStringExtra(EXTRA_MY_NAME)
        val opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME)

        callInfo = CallInfo(
            callType = callType,
            me = CallUserInfo(
                id = myName,
                name = myName
            ),
            recipient = CallUserInfo(
                id = opponentName,
                name = opponentName
            ),
            channelName = presenceChannelName
        )

        Log.d(TAG,"EXTRA_CALL_TYPE $callType")
        Log.d(TAG,"EXTRA_MY_NAME $myName")
        Log.d(TAG,"EXTRA_OPPONENT_NAME $opponentName")

        userInfoSubject.onNext(callInfo.recipient)
    }











    private fun applyState(callState: DirectCallState) {
        when(callState) {
            DirectCallState.INITIALIZING_CALLING_OUT -> {
                Log.d(TAG, "INITIALIZING_CALLING_OUT")
                beep()
                val notification = callNotificationHelper.getOutComingCallNotification(callInfo.callType, callInfo.recipient.name)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
                startCallActivity()
            }
            DirectCallState.CALLING_IN -> {
                Log.d(TAG, "CALLING_IN")
                vibrate()
                startIncomingCallSound()
                val notification = callNotificationHelper.getInComingCallNotification(callInfo.callType, callInfo.recipient.name)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
                wakeUp()
            }
            DirectCallState.AWAITING_OFFER -> {
                Log.d(TAG, "AWAITING_OFFER")
                stopBeeping()
                stopVibrate()
                stopIncomingCallSound()
                val notification = callNotificationHelper.getRunningCallNotification(callInfo.callType, callInfo.recipient.name)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
                startCallActivity()
            }
            DirectCallState.CALL_RUNNING -> {
                Log.d(TAG, "CALL_RUNNING")
                stopBeeping()
                stopVibrate()
                stopIncomingCallSound()
                val notification = callNotificationHelper.getRunningCallNotification(callInfo.callType, callInfo.recipient.name)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun wakeUp() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isInteractive = pm.isInteractive
        if (!isInteractive) {
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "call:${this.javaClass.name}")
            wakeLock?.acquire(60000)

            wakeLockCPU = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpu:${this.javaClass.name}")
            wakeLockCPU?.acquire(60000)
        }
    }

    override fun onFinishCall(message: String?) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        stopSelf()
    }











    //User interaction
    fun onAnswerClicked() {
        startService(getAnswerIntent(this))
    }

    fun onHangUpClicked() {
        startService(getDeclineIntent(this))
    }

    fun enableLocalAudio(enable: Boolean) {
        webRTCManager.enableAudio(enable)
    }








    inner class CallBinder : Binder() {
        fun getService(): DirectCallService = this@DirectCallService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    companion object {

        const val CHANNEL_ID_INCOMING_CALL = "CHANNEL_ID_INCOMING_CALL"
        const val CHANNEL_ID_CALL = "CHANNEL_ID_CALL"
        const val CALL_NOTIFICATION_ID = 1

        const val EXTRA_PRESENCE_CHANNEL_NAME = "EXTRA_PRESENCE_CHANNEL_NAME"
        const val EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE"
        const val EXTRA_MY_NAME = "EXTRA_MY_NAME"
        const val EXTRA_OPPONENT_NAME = "EXTRA_OPPONENT_NAME"

        const val ACTION_PERFORM_CALL = "ACTION_PERFORM_CALL"
        const val ACTION_RECEIVE_CALL = "ACTION_RECEIVE_CALL"
        const val ACTION_ANSWER_CALL = "ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "ACTION_DECLINE_CALL"

        fun startVideo(context: Context?, myName: String, opponentName: String) {
            Log.i(TAG,"startVideo")
            context?.startService(Intent(context, DirectCallService::class.java).apply {
                action = ACTION_PERFORM_CALL
                putExtra(EXTRA_PRESENCE_CHANNEL_NAME, "presence-$myName-$opponentName")
                putExtra(EXTRA_CALL_TYPE, CALL_TYPE_VIDEO)
                putExtra(EXTRA_MY_NAME, myName)
                putExtra(EXTRA_OPPONENT_NAME, opponentName)
            })
        }

        fun startVoice(context: Context?, myName: String, opponentName: String) {
            Log.i(TAG,"startVoice")
            context?.startService(Intent(context, DirectCallService::class.java).apply {
                action = ACTION_PERFORM_CALL
                putExtra(EXTRA_PRESENCE_CHANNEL_NAME, "presence-$myName-$opponentName")
                putExtra(EXTRA_CALL_TYPE, CALL_TYPE_VOICE)
                putExtra(EXTRA_MY_NAME, myName)
                putExtra(EXTRA_OPPONENT_NAME, opponentName)
            })
        }

        fun acceptVideo(context: Context?, myName: String, opponentName: String, presenceChannelName: String) {
            Log.i(TAG,"acceptVideo")
            context?.startService(Intent(context, DirectCallService::class.java).apply {
                action = ACTION_RECEIVE_CALL
                putExtra(EXTRA_PRESENCE_CHANNEL_NAME, presenceChannelName)
                putExtra(EXTRA_CALL_TYPE, CALL_TYPE_VIDEO)
                putExtra(EXTRA_MY_NAME, myName)
                putExtra(EXTRA_OPPONENT_NAME, opponentName)
            })
        }

        fun acceptVoice(context: Context?, myName: String, opponentName: String, presenceChannelName: String) {
            Log.i(TAG,"acceptVoice")
            context?.startService(Intent(context, DirectCallService::class.java).apply {
                action = ACTION_RECEIVE_CALL
                putExtra(EXTRA_PRESENCE_CHANNEL_NAME, presenceChannelName)
                putExtra(EXTRA_CALL_TYPE, CALL_TYPE_VOICE)
                putExtra(EXTRA_MY_NAME, myName)
                putExtra(EXTRA_OPPONENT_NAME, opponentName)
            })
        }






        fun getAnswerIntent(context: Context): Intent {
            return Intent(context, DirectCallService::class.java).apply {
                action = ACTION_ANSWER_CALL
            }
        }

        fun getDeclineIntent(context: Context): Intent {
            return Intent(context, DirectCallService::class.java).apply {
                action = ACTION_DECLINE_CALL
            }
        }
    }
}
