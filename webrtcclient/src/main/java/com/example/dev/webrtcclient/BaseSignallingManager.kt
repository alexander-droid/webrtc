package com.example.dev.webrtcclient

import android.support.annotation.CallSuper
import android.util.Log
import com.example.dev.webrtcclient.model.CallInfo
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpAuthorizer
import com.pusher.client.util.UrlEncodedConnectionFactory
import io.reactivex.disposables.CompositeDisposable

const val SIGNAL_ICE = "ICE_CANDIDATE"
const val SIGNAL_OFFER = "OFFER"
const val SIGNAL_ANSWER = "ANSWER"

const val EVENT_CALL = "rtc-call"
const val EVENT_CALL_DECLINE = "rtc-call-decline"
const val EVENT_CALL_BISY = "rtc-call-busy"

const val EVENT_CLIENT_RTC = "client-rtc"
const val EVENT_RTC_MEMBER_ADDED = "pusher:member_added"
const val EVENT_RTC_SUBSCRIBED = "pusher:subscription_succeeded"
const val EVENT_RTC_MEMBER_REMOVED = "pusher:member_removed"
const val EVENT_SERVER_RTC = "server-rtc"

abstract class BaseSignallingManager {

    private val disposable = CompositeDisposable()

    protected lateinit var callInfo: CallInfo

    protected var isFirstConnect = true

    protected lateinit var pusher: Pusher

    protected val delayedEmitList = mutableListOf<Runnable>()

    fun connect(callInfo: CallInfo, success: (() -> Unit)? = null) {
        Log.d(TAG, "connect")
        this.callInfo = callInfo
        val connectionFactory = UrlEncodedConnectionFactory(mutableMapOf<String, String>().apply {
            put("userId", callInfo.myId)
        })

        pusher = Pusher(ApiConfig.pusherApiKey(), PusherOptions().apply {
            setCluster("eu")
            authorizer = object: HttpAuthorizer("${ApiConfig.apiEndpoint()}/api/pusher/auth", connectionFactory) {
                override fun authorize(channelName: String?, socketId: String?): String {
                    val resp = super.authorize(channelName, socketId)
                    Log.d(TAG, "resp: $resp")
                    return resp
                }
            }
        })

        pusher.connect(object : ConnectionEventListener {

            override fun onConnectionStateChange(change: ConnectionStateChange?) {
                change?.currentState?.also { state ->
                    Log.d(TAG,"onConnectionStateChange $state, ${this.hashCode()}")
                }

                when(change?.currentState) {
                    ConnectionState.CONNECTED -> {
                        if (isFirstConnect) {
                            Log.w(TAG,"Pusher initialized")
                            success?.invoke()
                        }
                        isFirstConnect = false


                    }
                }
            }

            override fun onError(message: String?, code: String?, e: Exception?) {
                Log.e(TAG, "Pusher connection error: $message", e)
            }

        })
    }

    @CallSuper
    open fun disconnect() {
        Log.d(TAG, "disconnect")
        pusher.disconnect()
        disposable.dispose()
    }

    companion object {
        private const val TAG = "BaseSignallingManager"
    }
}