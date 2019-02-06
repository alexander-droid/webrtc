package com.example.dev.webrtcclient.call.direct

import android.support.annotation.WorkerThread
import android.util.Log
import com.example.dev.webrtcclient.*
import com.example.dev.webrtcclient.api.PusherApi
import com.example.dev.webrtcclient.model.CallInfo
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.message.MessageAnswer
import com.example.dev.webrtcclient.model.message.MessageDecline
import com.example.dev.webrtcclient.model.message.MessageIceCandidate
import com.example.dev.webrtcclient.model.message.MessageOffer
import com.example.dev.webrtcclient.model.request.RequestCall
import com.example.dev.webrtcclient.model.request.RequestDecline
import com.google.gson.Gson
import com.ihsanbal.logging.Level
import com.ihsanbal.logging.LoggingInterceptor
import com.pusher.client.channel.PresenceChannelEventListener
import com.pusher.client.channel.PrivateChannel
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.User
import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class DirectSignallingManager(var callback: Callback) : BaseSignallingManager() {

    private var myChannel: PrivateChannel? = null
    private var presenceChannel: PrivateChannel? = null

    private val pusherApiManager = Retrofit.Builder()
            .baseUrl(ApiConfig.apiEndpoint())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .client(OkHttpClient.Builder()
                    .addInterceptor(
                            LoggingInterceptor.Builder()
                                    .loggable(BuildConfig.DEBUG)
                                    .setLevel(Level.BASIC)
                                    .log(Platform.INFO)
                                    .tag("MyRequests")
                                    .build())
                    .build())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
            .create(PusherApi::class.java)

    private val myEventListener = object : PrivateChannelEventListener {

        override fun onEvent(channelName: String, eventName: String, data: String) {
            Log.v(TAG,"my onEvent channelName-$channelName, eventName-$eventName, data-$data")

            when (eventName) {
                EVENT_CALL_DECLINE -> {
                    val message = Gson().fromJson(data, MessageDecline::class.java)
                    callback.onDecline(message)
                }
            }
        }

        override fun onAuthenticationFailure(message: String?, e: Exception?) {
            Log.e(TAG,"opponent onAuthenticationFailure $message", e)
            callback.onError(message, e)
        }

        override fun onSubscriptionSucceeded(channelName: String) {
            Log.d(TAG,"opponent onSubscriptionSucceeded $channelName")
        }
    }

    private val presencelListener = object : PresenceChannelEventListener {

        override fun onUsersInformationReceived(channelName: String?, users: MutableSet<User>?) {
            Log.i(TAG,"presenceSubscription onUsersInformationReceived: $channelName, $users")
        }

        override fun userUnsubscribed(channelName: String, user: User) {
            Log.w(TAG,"presenceSubscription userUnsubscribed: $channelName, $user")
            map(user)?.also { recipientInfo ->
                callback.onOpponentUnsubscribed(recipientInfo)
            }
        }

        override fun userSubscribed(channelName: String?, user: User?) {
            Log.w(TAG,"presenceSubscription userSubscribed: $channelName, $user")
            callback.onGenerateOffer()
        }

        override fun onEvent(channelName: String, eventName: String, data: String) {
            Log.w(TAG,"presenceSubscription onEvent: $channelName, $eventName, $data")
            try {
                when (eventName) {
                    EVENT_CLIENT_RTC -> {
                        val jsonData = JSONObject(data)
                        if (jsonData.has("type")) {
                            val type = jsonData.getString("type")
                            when (type) {
                                SIGNAL_OFFER -> {
                                    val offer = Gson().fromJson(data, MessageOffer::class.java)
                                    Log.d(TAG,"onOffer: $offer")
                                    callback.onOffer(offer)
                                }
                                SIGNAL_ANSWER -> {
                                    val answer = Gson().fromJson(data, MessageAnswer::class.java)
                                    Log.d(TAG,"onAnswer: $answer")
                                    callback.onAnswer(answer)
                                }
                                SIGNAL_ICE -> {
                                    val ice = Gson().fromJson(data, MessageIceCandidate::class.java)
                                    Log.d(TAG,"onIce: $ice")
                                    callback.onIce(ice)
                                }
                            }
                        }
                    }
                }
            } catch (exc: Exception) {
                Log.e(TAG, "PRESENCE EVENT", exc)
            }
        }

        override fun onAuthenticationFailure(message: String?, e: Exception?) {
            Log.e(TAG,"presenceSubscription onAuthenticationFailure: $message", e)
            callback.onError(message, e)
        }

        override fun onSubscriptionSucceeded(channelName: String?) {
            Log.i(TAG,"presenceSubscription onSubscriptionSucceeded: $channelName")
        }
    }











    fun subscribeMyChannel(callInfo: CallInfo) {

        val privateName = if (callInfo.me.id.startsWith("private-")) {
            callInfo.me.id
        } else {
            "private-${callInfo.me.id}"
        }

        if (myChannel?.isSubscribed == true) {
            unsubscribeMyChannel()
        }
        myChannel = pusher.subscribePrivate(privateName, myEventListener)
        myChannel?.bind(EVENT_CALL_BISY, myEventListener)
        myChannel?.bind(EVENT_CALL_DECLINE, myEventListener)
    }

    fun unsubscribeMyChannel() {
        myChannel?.name?.also { channelName ->
            myChannel?.unbind(EVENT_CALL_BISY, myEventListener)
            myChannel?.unbind(EVENT_CALL_DECLINE, myEventListener)
            pusher.unsubscribe(myChannel?.name)
        }
    }

    fun subscribePresenceChannel(callInfo: CallInfo) {
        presenceChannel = pusher.subscribePresence(callInfo.channelName, presencelListener, EVENT_CLIENT_RTC)


    }









    @WorkerThread
    fun emitCallAttempt(callInfo: CallInfo) {
        Log.d(TAG,"emitCallAttempt")
        pusherApiManager.requestCall(RequestCall(
            event = EVENT_CALL,
            to = listOf("private-${callInfo.recipient.id}"),
            data = RequestCall.Data(
                    caller = callInfo.me.id,
                    channel = callInfo.channelName,
                    time = System.currentTimeMillis(),
                    type = callInfo.callType
            )
        )).execute()
    }

    @WorkerThread
    fun emitCallDecline(callInfo: CallInfo) {
        Log.d(TAG,"emitCallDecline ${RequestDecline(
            event = EVENT_CALL_DECLINE,
            to = listOf("private-${callInfo.recipient.id}"),
            data = RequestDecline.Data(
                channel = callInfo.channelName,
                time = System.currentTimeMillis(),
                busy = false
            )
        )}")
        pusherApiManager.requestDecline(RequestDecline(
            event = EVENT_CALL_DECLINE,
            to = listOf("private-${callInfo.recipient.id}"),
            data = RequestDecline.Data(
                    channel = callInfo.channelName,
                    time = System.currentTimeMillis(),
                    busy = false
            )
        )).execute()
    }

    @WorkerThread
    fun emitBusy(callInfo: CallInfo) {
        Log.d(TAG,"emitBusy: ${callInfo.recipient.id}")
        pusherApiManager.requestDecline(RequestDecline(
            event = EVENT_CALL_DECLINE,
            to = listOf("private-${callInfo.recipient.id}"),
            data = RequestDecline.Data(
                    channel = callInfo.channelName,
                    time = System.currentTimeMillis(),
                    busy = true
            )
        )).execute()
    }










    fun emitOffer(callInfo: CallInfo, offer: SessionDescription) {
        Log.d(TAG,"emitOffer")

        presenceChannel?.trigger(EVENT_CLIENT_RTC, Gson().toJson(MessageOffer(
            type = SIGNAL_OFFER,
            time = System.currentTimeMillis(),
            data = MessageOffer.Data(
                from = callInfo.me.id,
                to = callInfo.recipient.id,
                type = callInfo.callType,
                description = MessageOffer.Data.Description(
                    type = offer.type.canonicalForm(),
                    sdp = offer.description
                )
            )
        )))
    }

    fun emitIceCandidate(callInfo: CallInfo, ice: IceCandidate) {
        Log.d(TAG,"emitIceCandidate: $ice")
        presenceChannel?.trigger(EVENT_CLIENT_RTC, Gson().toJson(MessageIceCandidate(
            type = SIGNAL_ICE,
            time = System.currentTimeMillis(),
            data = MessageIceCandidate.Data(
                from = callInfo.me.id,
                to = callInfo.recipient.id,
                candidate = MessageIceCandidate.Data.Candidate(
                    candidate = ice.sdp,
                    sdpMid = ice.sdpMid,
                    sdpMLineIndex = ice.sdpMLineIndex
                )
            )
        )))
    }

    fun emitAnswer(callInfo: CallInfo, answer: SessionDescription) {
        Log.d(TAG,"emitAnswer: $answer")
        presenceChannel?.trigger(EVENT_CLIENT_RTC, Gson().toJson(MessageAnswer(
            type = SIGNAL_ANSWER,
            time = System.currentTimeMillis(),
            data = MessageAnswer.Data(
                from = callInfo.me.id,
                to = callInfo.recipient.id,
                description = MessageAnswer.Data.Description(
                    type = answer.type.canonicalForm(),
                    sdp = answer.description
                )
            )
        )))
    }


    override fun disconnect() {
        myChannel?.unbind(EVENT_CALL_BISY, myEventListener)
        myChannel?.unbind(EVENT_CALL_DECLINE, myEventListener)
        if (myChannel?.isSubscribed == true) {
            myChannel?.name?.also {
                pusher.unsubscribe(it)
            }
        }

        presenceChannel?.unbind(EVENT_CLIENT_RTC, presencelListener)
        if (presenceChannel?.isSubscribed == true) {
            presenceChannel?.name?.also {
                pusher.unsubscribe(it)
            }
        }
        super.disconnect()
    }



    companion object {
        private const val TAG = "DirectSignallingManager"
    }

    interface Callback {
        fun onDecline(decline: MessageDecline? = null)

        fun onOffer(offer: MessageOffer)
        fun onAnswer(answer: MessageAnswer)
        fun onIce(ice: MessageIceCandidate)

        fun onGenerateOffer()
        fun onError(message: String?, exception: Exception? = null)
        fun onOpponentUnsubscribed(userInfo: CallUserInfo)
    }

}