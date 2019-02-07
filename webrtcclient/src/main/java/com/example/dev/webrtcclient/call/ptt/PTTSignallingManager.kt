package com.example.dev.webrtcclient.call.ptt

import android.util.Log
import com.example.dev.webrtcclient.*
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.GroupCallInfo
import com.example.dev.webrtcclient.model.message.MessageAnswer
import com.example.dev.webrtcclient.model.message.MessageIceCandidate
import com.example.dev.webrtcclient.model.message.MessageOffer
import com.google.gson.Gson
import com.pusher.client.channel.PresenceChannel
import com.pusher.client.channel.PresenceChannelEventListener
import com.pusher.client.channel.User
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class PTTSignallingManager(var callback: Callback): BaseSignallingManager() {

    private var groupChannel: PresenceChannel? = null

    fun subscribeGroup(callInfo: GroupCallInfo) {
        groupChannel = pusher.subscribePresence(callInfo.channelName, object : PresenceChannelEventListener {
            override fun onUsersInformationReceived(channelName: String, users: MutableSet<User>) {
                val recipientInfoList = mutableListOf<CallUserInfo>()
                users.forEach { user ->
                    map(user)?.also { recipientInfo ->
                        Log.i(TAG, "onUsersInformationReceived ${recipientInfo}")
                        if (callInfo.me.id != recipientInfo.id) {
                            recipientInfoList.add(recipientInfo)
                        }
                    }
                }

                callback.onRecipientsInformationReceived(recipientInfoList)
            }

            override fun userUnsubscribed(channelName: String, user: User) {
                map(user)?.also { recipientInfo ->
                    callback.onRecipientUnsubscribed(recipientInfo)
                }
            }

            override fun userSubscribed(channelName: String, user: User) {
                map(user)?.also { recipientInfo ->
                    callback.onRecipientSubscribed(recipientInfo)
                }
            }

            override fun onEvent(channelName: String, eventName: String, data: String) {
                try {
                    if (channelName == callInfo.channelName) {
                        when(eventName) {
                            EVENT_CLIENT_RTC -> {
                                handleClientEvent(data)
                            }
                            EVENT_GROUP_TALK -> {
                                handleTalkingEvent(data)
                            }
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(TAG, "OnEventError", exc)
                }
            }

            override fun onAuthenticationFailure(message: String?, e: Exception?) {
                callback.onError(message, e)
            }

            override fun onSubscriptionSucceeded(channelName: String) {

            }
        }, EVENT_CLIENT_RTC, EVENT_GROUP_TALK)
    }












    private fun handleClientEvent(data: String) {
        val jsonData = JSONObject(data)
        if (jsonData.has("type")) {
            val type = jsonData.getString("type")
            when(type) {
                SIGNAL_OFFER -> {
                    fetchOffer(data)
                }
                SIGNAL_ANSWER -> {
                    fetchAnswer(data)
                }
                SIGNAL_ICE -> {
                    fetchIce(data)
                }
            }
        }
    }

    private fun fetchOffer(data: String) {
        val offer = Gson().fromJson(data, MessageOffer::class.java)
        Log.i(TAG, "OFFER_RECEIVED")
        callback.onOffer(offer)
    }

    private fun fetchAnswer(data: String) {
        val answer = Gson().fromJson(data, MessageAnswer::class.java)
        Log.i(TAG, "ANSWER_RECEIVED")
        callback.onAnswer(answer)
    }

    private fun fetchIce(data: String) {
        val ice = Gson().fromJson(data, MessageIceCandidate::class.java)
        Log.i(TAG, "ICE_RECEIVED")
        callback.onIce(ice)
    }

    private fun handleTalkingEvent(data: String) {
        val jsonData = JSONObject(data)
        var talkingUserId: String? = null
        var isSpeaking = false
        if (jsonData.has("talking")) {
            talkingUserId = jsonData.getString("talking")
            isSpeaking = true
        } else if (jsonData.has("stop")) {
            talkingUserId = jsonData.getString("stop")
            isSpeaking = false
        }

        if (talkingUserId != null) {
            val recipient = CallUserInfo(
                id = talkingUserId,
                name = talkingUserId
            )

            Log.v(TAG, "TALKING_RECEIVED: ${recipient.id}, isSpeaking: $isSpeaking")
            callback.onUserTalking(recipient, isSpeaking)
        }
    }











    fun emitOffer(callInfo: GroupCallInfo, recipientInfo: CallUserInfo, offer: SessionDescription) {
        Log.d(TAG,"emitOffer")
        groupChannel?.trigger(EVENT_CLIENT_RTC, Gson().toJson(MessageOffer(
            type = SIGNAL_OFFER,
            time = System.currentTimeMillis(),
            data = MessageOffer.Data(
                from = callInfo.me.id,
                to = recipientInfo.id,
                type = callInfo.callType,
                description = MessageOffer.Data.Description(
                    type = offer.type.canonicalForm(),
                    sdp = offer.description
                )
            )
        )))
    }

    fun emitIceCandidate(callInfo: GroupCallInfo, recipientInfo: CallUserInfo, ice: IceCandidate) {
        Log.d(TAG,"emitIceCandidate")
        groupChannel?.trigger(EVENT_CLIENT_RTC, Gson().toJson(MessageIceCandidate(
            type = SIGNAL_ICE,
            time = System.currentTimeMillis(),
            data = MessageIceCandidate.Data(
                from = callInfo.me.id,
                to = recipientInfo.id,
                candidate = MessageIceCandidate.Data.Candidate(
                    candidate = ice.sdp,
                    sdpMid = ice.sdpMid,
                    sdpMLineIndex = ice.sdpMLineIndex
                )
            )
        )))
    }

    fun emitAnswer(callInfo: GroupCallInfo, recipientInfo: CallUserInfo, answer: SessionDescription) {
        Log.d(TAG,"emitAnswer")
        groupChannel?.trigger(EVENT_CLIENT_RTC, Gson().toJson(MessageAnswer(
            type = SIGNAL_ANSWER,
            time = System.currentTimeMillis(),
            data = MessageAnswer.Data(
                from = callInfo.me.id,
                to = recipientInfo.id,
                description = MessageAnswer.Data.Description(
                    type = answer.type.canonicalForm(),
                    sdp = answer.description
                )
            )
        )))
    }











    fun emitStartTalking(recipientInfo: CallUserInfo) {
        Log.d(TAG,"emitStartTalking: $recipientInfo")
        val jsonData = JSONObject()
        jsonData.put("talking", recipientInfo.id)
        groupChannel?.trigger(EVENT_GROUP_TALK, jsonData.toString())
    }

    fun emitStopTalking(recipientInfo: CallUserInfo) {
        Log.v(TAG,"emitStopTalking: $recipientInfo")
        val jsonData = JSONObject()
        jsonData.put("stop", recipientInfo.id)
        try {
            groupChannel?.trigger(EVENT_GROUP_TALK, jsonData.toString())
        } catch (exc: Exception) {
            Log.e(TAG, "Error while emitting", exc)
            //TODO
        }
    }












    fun getGroupMembers(callInfo: GroupCallInfo): MutableList<CallUserInfo> {
        val members = mutableListOf<CallUserInfo>()
        groupChannel?.users?.forEach { user ->
            map(user)?.also { userInfo ->
                if (callInfo.me.id != userInfo.id) {
                    members.add(userInfo)
                }
            }
        }
        return members
    }












    interface Callback {
        fun onError(message: String?, exception: Exception? = null, leave: Boolean = false)
        fun onRecipientsInformationReceived(recipientInfoList: MutableList<CallUserInfo>)
        fun onRecipientSubscribed(recipientInfo: CallUserInfo)
        fun onRecipientUnsubscribed(recipientInfo: CallUserInfo)
        fun onOffer(offer: MessageOffer)
        fun onAnswer(answer: MessageAnswer)
        fun onIce(ice: MessageIceCandidate)
        fun onUserTalking(recipient: CallUserInfo, isSpeaking: Boolean)
    }

    companion object {
        private const val TAG = "SignallingManager"
    }
}