package com.example.dev.webrtcclient.call.ptt

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.example.dev.webrtcclient.model.GroupCallInfo
import io.reactivex.disposables.CompositeDisposable

class PTTCallService : Service(), PTTWebRTCManager.Callback {

    private var callInfo: GroupCallInfo? = null

    private lateinit var webRTCManager: PTTWebRTCManager

    private val disposable = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        webRTCManager = PTTWebRTCManager(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
        webRTCManager.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG,"onStartCommand ${intent?.action}")

        when(intent?.action) {
            ACTION_JOIN_GROUP -> {
                retrieveExtras(intent)
//                PTTCallActivity.start(this)
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
            myId = myName,
            myName = myName,
            channelName = channelName
        )

        Log.d(TAG,"EXTRA_CALL_TYPE $callType")
        Log.d(TAG,"EXTRA_MY_NAME $myName")
        Log.d(TAG,"EXTRA_GROUP_CHANNEL_NAME $channelName")

//        userInfoSubject.onNext(CallUserInfo(
//            id = opponentName,
//            data = CallUserInfo.Data(
//                name = opponentName
//            )
//        ))
    }






    //User interaction
    fun leave() {
        startService(getLeaveIntent(this))
    }

    fun startTalking() {
        webRTCManager.startTalking(callInfo)
    }

    fun stopTalking() {
        webRTCManager.stopTalking()
    }




    //WebRTC callbacks
    override fun onLeave(message: String?) {
        message?.also {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }
        stopSelf()
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
