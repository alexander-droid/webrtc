package com.example.dev.webrtcclient.call.direct

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.example.dev.webrtcclient.call.direct.DirectWebRTCManager.Companion.CALL_TYPE_VIDEO
import com.example.dev.webrtcclient.call.direct.DirectWebRTCManager.Companion.CALL_TYPE_VOICE
import com.example.dev.webrtcclient.call.direct.ui.DirectCallActivity
import com.example.dev.webrtcclient.log.SimpleEvent
import com.example.dev.webrtcclient.model.CallInfo
import com.example.dev.webrtcclient.model.CallState
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.CallViewState
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import org.webrtc.EglBase
import org.webrtc.VideoTrack


class DirectCallService : Service(), DirectWebRTCManager.Callback {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockCPU: PowerManager.WakeLock? = null

    private val disposable = CompositeDisposable()

    private val userInfoSubject = BehaviorSubject.create<CallUserInfo>()
    val userInfoObservable: Observable<CallUserInfo>
        get() = userInfoSubject

    val callStateObservable: Observable<CallState>
        get() = webRTCManager.callStateObservable

    val loglist: MutableList<SimpleEvent>
        get() = webRTCManager.logList
    val logObservable: Observable<SimpleEvent>
        get() = webRTCManager.logObservable

    val callViewStateObservable: Observable<CallViewState>
        get() = webRTCManager.callStateObservable
            .map {
                return@map when(it) {
                    CallState.NONE -> CallViewState.NONE
                    CallState.INITIALIZING_CALLING_IN,
                    CallState.CALLING_IN -> {
                        CallViewState.CALLING_IN
                    }
                    CallState.INITIALIZING_CALLING_OUT,
                    CallState.CALLING_OUT,
                    CallState.CREATING_OFFER,
                    CallState.AWAITING_ANSWER,
                    CallState.SETTING_ANSWER -> {
                        CallViewState.CALLING_OUT
                    }
                    CallState.AWAITING_OFFER,
                    CallState.SETTING_OFFER,
                    CallState.CREATING_ANSWER,
                    CallState.CALL_RUNNING -> CallViewState.CALL_RUNNING
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

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "onCreate")

        callNotificationHelper = DirectCallNotificationHelper(this)
        webRTCManager = DirectWebRTCManager(this, this)

        disposable.add(callStateObservable.subscribe {
            applyState(it)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG,"onDestroy")
        disposable.dispose()
        webRTCManager.release()

        wakeLock?.release()
        wakeLockCPU?.release()
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



    private fun retrieveExtras(intent: Intent?) {
        intent?:return
        val presenceChannelName = intent.getStringExtra(EXTRA_PRESENCE_CHANNEL_NAME)
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE)
        val myName = intent.getStringExtra(EXTRA_MY_NAME)
        val opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME)

        callInfo = CallInfo(
            callType = callType,
            myId = myName,
            myName = myName,
            opponentId = opponentName,
            opponentName = opponentName,
            channelName = presenceChannelName
        )

        Log.d(TAG,"EXTRA_CALL_TYPE $callType")
        Log.d(TAG,"EXTRA_MY_NAME $myName")
        Log.d(TAG,"EXTRA_OPPONENT_NAME $opponentName")

        userInfoSubject.onNext(CallUserInfo(
                id = opponentName,
                data = CallUserInfo.Data(
                        name = opponentName
                )
        ))
    }











    private fun applyState(callState: CallState) {
        when(callState) {
            CallState.INITIALIZING_CALLING_OUT -> {
                val notification = callNotificationHelper.getOutComingCallNotification(callInfo.callType, callInfo.opponentName)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
                startCallActivity()
            }
            CallState.CALLING_IN -> {
                val notification = callNotificationHelper.getInComingCallNotification(callInfo.callType, callInfo.opponentName)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
                wakeUp()
            }
            CallState.AWAITING_OFFER -> {
                val notification = callNotificationHelper.getRunningCallNotification(callInfo.callType, callInfo.opponentName)
                startForeground(DirectCallService.CALL_NOTIFICATION_ID, notification)
                startCallActivity()
            }
            CallState.CALL_RUNNING -> {
                val notification = callNotificationHelper.getRunningCallNotification(callInfo.callType, callInfo.opponentName)
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

        private const val TAG = "DirectCallService"

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
