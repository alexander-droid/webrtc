package com.example.dev.webrtcclient.call.ptt

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.support.annotation.WorkerThread
import android.util.Log
import com.example.dev.webrtcclient.BaseWebRTCManager
import com.example.dev.webrtcclient.CustomPeerConnectionObserver
import com.example.dev.webrtcclient.CustomSdpObserver
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.GroupCallInfo
import com.example.dev.webrtcclient.model.GroupCallState
import com.example.dev.webrtcclient.model.message.MessageAnswer
import com.example.dev.webrtcclient.model.message.MessageIceCandidate
import com.example.dev.webrtcclient.model.message.MessageOffer
import com.example.dev.webrtcclient.model.response.TurnServer
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.webrtc.*

class PTTWebRTCManager(
    private val context: Context,
    private val callback: Callback
) : BaseWebRTCManager(), PTTSignallingManager.Callback {

    private val recipientsSubject = BehaviorSubject.create<MutableList<CallUserInfo>>()
    private val recipientAddedSubject = PublishSubject.create<CallUserInfo>()
    private val recipientRemovedSubject = PublishSubject.create<CallUserInfo>()

    val recipientsObservable: Observable<List<CallUserInfo>>
        get() = recipientsSubject
            .map { it.toList() }
            .observeOn(AndroidSchedulers.mainThread())

    val recipientAddedObservable: Observable<CallUserInfo>
        get() = recipientAddedSubject
            .observeOn(AndroidSchedulers.mainThread())

    val recipientRemovedObservable: Observable<CallUserInfo>
        get() = recipientRemovedSubject
            .observeOn(AndroidSchedulers.mainThread())

    private val callStateSubject = BehaviorSubject.createDefault(GroupCallState.none())
    val callStateObservable: Observable<GroupCallState>
        get() = callStateSubject.observeOn(AndroidSchedulers.mainThread())

    private val messageSubject = PublishSubject.create<String>()
    val messageObservable: Observable<String>
        get() = messageSubject.observeOn(AndroidSchedulers.mainThread())

    private var groupCallInfo: GroupCallInfo? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerThread = HandlerThread("workerHandlerThread")
    private var workerHandler: Handler

    private val signalingManager = PTTSignallingManager(this)

    private val peerConnectionMap = mutableMapOf<String, MyPeerConnection>()

    private lateinit var rtcConfig: PeerConnection.RTCConfiguration
    private val peerIceServers = mutableListOf<PeerConnection.IceServer>()
    private lateinit var rootEglBase: EglBase
    lateinit var peerConnectionFactory: PeerConnectionFactory

    init {
        handlerThread.start()
        workerHandler = Handler(handlerThread.looper)

        workerHandler.post {
            try {
                val turnServer = requestIceServers()
                if (turnServer != null) {
                    setIceServers(turnServer)
                } else {
                    onError( "Failed to receive ice servers", null, true)
                }
            } catch (exc: Exception) {
                onError( "Failed to receive ice servers", exc, true)
            }
        }
    }

    private fun setIceServers(turnServer: TurnServer) {
        turnServer.iceServerList?.iceServers?.forEach { iceServer ->
            if (iceServer.credential == null) {
                val peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer()
                peerIceServers.add(peerIceServer)
            } else {
                val peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                    .setUsername(iceServer.username)
                    .setPassword(iceServer.credential)
                    .createIceServer()
                peerIceServers.add(peerIceServer)
            }
        }
    }

    private fun initInternal() {
        rootEglBase = EglBase.create()

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        rtcConfig = PeerConnection.RTCConfiguration(peerIceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
    }

    fun connect(callInfo: GroupCallInfo?) {
        workerHandler.post {
            callInfo?:return@post
            groupCallInfo = callInfo
            signalingManager.connect(callInfo.me.id)
            signalingManager.subscribeGroup(callInfo)
        }
    }










    //User interaction
    fun startTalking() {
        workerHandler.post {
            try {
                if (callStateSubject.value?.state == GroupCallState.State.NONE) {
                    val callInfo = groupCallInfo
                    callInfo?:return@post
                    val members = signalingManager.getGroupMembers(callInfo)
                    if (members.isNotEmpty()) {
                        callStateSubject.onNext(GroupCallState.meSpeaking(callInfo.me))
                        signalingManager.emitStartTalking(callInfo.me)
                        createOffers(members)
                    } else {
                        messageSubject.onNext("No one here")
                        callStateSubject.value?.also { state ->
                            callStateSubject.onNext(state)
                        }
                    }
                } else {
                    callStateSubject.value?.also { state ->
                        callStateSubject.onNext(state)
                    }
                }
            } catch (exc: Exception) {
                onError("Error while creating offers", exc)
            }
        }
    }

    private fun createOffers(members: List<CallUserInfo>) {
        initInternal()
        members.forEach { userInfo ->
            val myPeer = MyPeerConnection(userInfo, true)
            peerConnectionMap[userInfo.id] = myPeer
        }

        peerConnectionMap.forEach {
            it.value.createOffer()
        }
    }

    fun stopTalking() {
        workerHandler.post {
            try {
                if (callStateSubject.value?.state == GroupCallState.State.ME_SPEAKING) {
                    val callInfo = groupCallInfo
                    callInfo?:return@post
                    signalingManager.emitStopTalking(callInfo.me)
                    releasePeers(callInfo.me)
                }
            } catch (exc: Exception) {
                onError("Error while releasing peers", exc)
            }
        }
    }










    //Signalling callbacks
    override fun onRecipientsInformationReceived(recipientInfoList: MutableList<CallUserInfo>) {
        workerHandler.post {
            recipientsSubject.onNext(recipientInfoList)
        }
    }

    override fun onRecipientSubscribed(recipientInfo: CallUserInfo) {
        workerHandler.post {
            recipientAddedSubject.onNext(recipientInfo)
            recipientsSubject.value?.also { list ->
                list.add(recipientInfo)
                recipientsSubject.onNext(list)
            }
        }
    }

    override fun onRecipientUnsubscribed(recipientInfo: CallUserInfo) {
        workerHandler.post {
            recipientRemovedSubject.onNext(recipientInfo)
            recipientsSubject.value?.also { list ->
                list.remove(recipientInfo)
                recipientsSubject.onNext(list)
            }

            peerConnectionMap.remove(recipientInfo.id)?.also { peer ->
                peer.release()
            }
        }
    }

    override fun onUserTalking(recipient: CallUserInfo, isSpeaking: Boolean) {
        workerHandler.post {
            Log.w("rioferu", "onUserTalking: $isSpeaking")
            if (!isSpeaking && shouldAcceptUserStopSpeaking(recipient)) {
                releasePeers(recipient)
            }
        }
    }

    override fun onOffer(offer: MessageOffer) {
        workerHandler.post {
            val callInfo = groupCallInfo
            callInfo?:return@post
            if (shouldAcceptOffer(offer)) {
                signalingManager.getGroupMembers(callInfo).also { members ->
                    members.forEach { member ->
                        if (member.id == offer.data.from) {
                            Log.w(TAG, "ON_OFFER ${offer.data.from}->${offer.data.to} ${offer.data.description}")
                            callStateSubject.onNext(GroupCallState.recipientSpeaking(member))
                            initInternal()
                            val myPeer = MyPeerConnection(member, false)
                            peerConnectionMap[member.id] = myPeer
                            myPeer.setOffer(offer)

                            Log.d("rioferu", "onOffer: ${member.id}")
                            return@also
                        }
                    }
                }
            }
        }
    }

    override fun onAnswer(answer: MessageAnswer) {
        workerHandler.post {
            if (shouldAcceptAnswer(answer)) {
                val callInfo = groupCallInfo
                callInfo?:return@post
                peerConnectionMap[answer.data.from]?.also { peer ->
                    Log.w(TAG, "ON_ANSWER ${answer.data.from}->${answer.data.to} ${answer.data.description}")
                    peer.setAnswer(answer)
                }
            }
        }
    }

    override fun onIce(ice: MessageIceCandidate) {
        workerHandler.post {
            if (shouldAcceptIce(ice)) {
                val callInfo = groupCallInfo
                callInfo?:return@post
                peerConnectionMap[ice.data.from]?.also { peer ->
                    Log.w(TAG, "ON_ICE ${ice.data.from}->${ice.data.to} ${ice.data.candidate}")
                    peer.addIceCandidate(ice)
                }
            }
        }
    }









    private fun shouldAcceptUserStopSpeaking(recipient: CallUserInfo): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        if (callInfo.me.id != recipient.id
            && callStateSubject.value?.state == GroupCallState.State.RECIPIENT_SPEAKING
            && callStateSubject.value?.lastSpeaker?.id == recipient.id) {
            return true
        }

        return false
    }

    private fun shouldAcceptOffer(offer: MessageOffer): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        if (callInfo.me.id == offer.data.to && callStateSubject.value?.state == GroupCallState.State.NONE) {
            return true
        }

        return false
    }

    private fun shouldAcceptAnswer(answer: MessageAnswer): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        if (callInfo.me.id == answer.data.to && callStateSubject.value?.state == GroupCallState.State.ME_SPEAKING) {
            return peerConnectionMap.contains(answer.data.from)
        }

        return false
    }

    private fun shouldAcceptIce(ice: MessageIceCandidate): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        if (callInfo.me.id == ice.data.to) {
            return peerConnectionMap.contains(ice.data.from)
        }

        return false
    }










    @WorkerThread
    private fun emitIceCandidate(userInfo: CallUserInfo, ice: IceCandidate) {
        workerHandler.post {
            try {
                groupCallInfo?.also { callInfo ->
                    Log.v(TAG, "emitIceCandidate ${userInfo.id}")
                    signalingManager.emitIceCandidate(callInfo, userInfo, ice)
                }
            } catch (exc: Exception) {
                onError("Failed emitting ice candidate", exc)
            }
        }
    }

    @WorkerThread
    private fun emitOffer(userInfo: CallUserInfo, offer: SessionDescription) {
        workerHandler.post {
            try {
                groupCallInfo?.also { callInfo ->
                    Log.v(TAG, "emitOffer ${userInfo.id}")
                    signalingManager.emitOffer(callInfo, userInfo, offer)
                }
            } catch (exc: Exception) {
                onError("Failed sending offer", exc)
            }
        }
    }

    @WorkerThread
    private fun emitAnswer(userInfo: CallUserInfo, answer: SessionDescription) {
        workerHandler.post {
            try {
                groupCallInfo?.also { callInfo ->
                    Log.v(TAG, "emitAnswer ${userInfo.id}")
                    signalingManager.emitAnswer(callInfo, userInfo, answer)
                }
            } catch (exc: Exception) {
                onError("Failed sending answer", exc)
            }
        }
    }












    override fun onError(message: String?, exception: Exception?, leave: Boolean) {
        workerHandler.post {
            Log.e(TAG, message, exception)
            message?.also {
                messageSubject.onNext(it)
            }

            if (callStateSubject.value?.state == GroupCallState.State.ME_SPEAKING) {
                groupCallInfo?.also { callInfo ->
                    signalingManager.emitStopTalking(callInfo.me)
                }
            }

            releasePeers()
            if (leave) {
                callback.onLeave()
            }
        }
    }

    private fun releasePeers(lastSpokenUser: CallUserInfo? = null) {
        lastSpokenUser?.also { user ->
            callStateSubject.onNext(GroupCallState.stoppedSpeaking(user))
        } ?: run {
            callStateSubject.onNext(GroupCallState.none())
        }
        peerConnectionMap.forEach {
            it.value.release()
        }
        peerConnectionMap.clear()
        workerHandler.removeCallbacksAndMessages(null)
    }

    fun release() {
        workerHandler.post {
            try {
                recipientAddedSubject.onComplete()
                recipientRemovedSubject.onComplete()
                callStateSubject.onComplete()
                recipientsSubject.onComplete()
                signalingManager.disconnect()
                releasePeers()
                workerHandler.removeCallbacksAndMessages(null)
                mainHandler.removeCallbacksAndMessages(null)
                handlerThread.quit()
            } catch (exc: Throwable) {
                Log.e(TAG, "error", exc)
            }
        }
    }

    companion object {
        const val TAG = "WebRTCManager"
    }

    interface Callback {
        fun onLeave(message: String? = null)

    }











    inner class MyPeerConnection(private val userInfo: CallUserInfo, private val enableOutput: Boolean) {

        private lateinit var localPeer: PeerConnection

        init {
            createPeerConnection()
        }

        private fun createPeerConnection() {
            localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    Log.v("my_peer", "${userInfo.id} iceCandidateCreated")
                    emitIceCandidate(userInfo, iceCandidate)
                }
            })!!

            if (enableOutput) {
                val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
                val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
                localAudioTrack.setVolume(10.0)

                val stream = peerConnectionFactory.createLocalMediaStream("102")
                stream.addTrack(localAudioTrack)
                localPeer.addStream(stream)
            }
        }

        fun createOffer() {
            Log.v("my_peer", "${userInfo.id} createOffer")
            val sdpConstraints = MediaConstraints()
            sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            localPeer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    Log.v("my_peer", "${userInfo.id} createOfferSuccess")
                    localPeer.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                    emitOffer(userInfo, sessionDescription)
                }

                override fun onCreateFailure(s: String) {
                    onError(s)
                }

                override fun onSetFailure(s: String) {}
                override fun onSetSuccess() {}
            }, sdpConstraints)
        }

        fun createAnswer() {
            Log.v("my_peer", "${userInfo.id} createAnswer")
            localPeer.createAnswer(object : SdpObserver {

                override fun onCreateSuccess(sdp: SessionDescription) {
                    Log.v("my_peer", "${userInfo.id} createAnswerSuccess")
                    localPeer.setLocalDescription(CustomSdpObserver("localSetLocal"), sdp)
                    emitAnswer(userInfo, sdp)
                }

                override fun onCreateFailure(s: String?) {
                    onError(s)
                }

                override fun onSetFailure(s: String?) {}
                override fun onSetSuccess() {}
            }, MediaConstraints())
        }










        fun addIceCandidate(ice: MessageIceCandidate) {
            Log.w("my_peer", "${userInfo.id} addIceCandidate")
            ice.data.candidate?.also { candidate ->
                localPeer.addIceCandidate(IceCandidate(candidate.sdpMid, ice.data.candidate.sdpMLineIndex, candidate.candidate))
            }
        }

        fun setOffer(offer: MessageOffer) {
            Log.w(TAG, "ON_OFFER setOffer")
            Log.d("my_peer", "${userInfo.id} setOffer")
            localPeer.setRemoteDescription(object : SdpObserver {
                override fun onSetFailure(s: String?) {
                    onError(s)
                }

                override fun onSetSuccess() {
                    Log.d("my_peer", "${userInfo.id} setOfferSuccess")
                    workerHandler.post {
                        try {
                            createAnswer()
                        } catch (exc: Exception) {
                            onError("Failed creating answer", exc)
                        }
                    }
                }

                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(s: String?) {}

            }, SessionDescription(SessionDescription.Type.OFFER, offer.data.description.sdp))
        }

        fun setAnswer(answer: MessageAnswer) {
            Log.d("my_peer", "${userInfo.id} setAnswer")
            localPeer.setRemoteDescription(object : SdpObserver {
                override fun onSetFailure(s: String?) {
                    onError(s)
                }

                override fun onSetSuccess() {
                    Log.d("my_peer", "${userInfo.id} setAnswerSuccess")
                    workerHandler.post {

                    }
                }

                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(s: String?) {}
            }, SessionDescription(SessionDescription.Type.fromCanonicalForm(answer.type.toLowerCase()), answer.data.description.sdp))
        }

        fun release() {
            Log.e(TAG, "release ${userInfo.id}")
            Log.e("my_peer", "release ${userInfo.id}")
            try {
                localPeer.dispose()
            } catch (exc: Exception) {
                Log.e("my_peer", "error while release peer", exc)
            }
        }
    }
}