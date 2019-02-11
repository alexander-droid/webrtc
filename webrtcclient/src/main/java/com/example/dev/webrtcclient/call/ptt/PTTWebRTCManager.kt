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
import java.util.*

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
            val sessionId = UUID.randomUUID().toString()
            val myPeer = MyPeerConnection(userInfo, true, sessionId)
            peerConnectionMap[userInfo.id] = myPeer
            myPeer.createOffer(sessionId)
        }
    }

    fun stopTalking() {
        workerHandler.post {
            try {
                callStateSubject.value?.state?.also { state ->
                    if (state == GroupCallState.State.ME_SPEAKING) {
                        val callInfo = groupCallInfo
                        callInfo?:return@post
                        signalingManager.emitStopTalking(callInfo.me)
                        releasePeers(callInfo.me)
                    }
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

            if (callStateSubject.value?.state == GroupCallState.State.ME_SPEAKING) {
                val callInfo = groupCallInfo
                callInfo?:return@post
//                signalingManager.emitStartTalking(callInfo.me)

                val sessionId = UUID.randomUUID().toString()
                val myPeer = MyPeerConnection(recipientInfo, true, sessionId)
                peerConnectionMap[recipientInfo.id] = myPeer
                myPeer.createOffer(sessionId)
            }
        }
    }

    override fun onRecipientUnsubscribed(recipientInfo: CallUserInfo) {
        Log.e("SignallingManager", "onRecipientUnsubscribed: $recipientInfo")
        workerHandler.post {
            recipientRemovedSubject.onNext(recipientInfo)
            recipientsSubject.value?.also { list ->
                Log.e("SignallingManager", "onRecipientUnsubscribed: remove ${list.size}")
                list.remove(recipientInfo)
                Log.e("SignallingManager", "onRecipientUnsubscribed: remove ${list.size}")
                recipientsSubject.onNext(list)
            }

            peerConnectionMap.remove(recipientInfo.id)?.also { peer ->
                if (callStateSubject.value?.state == GroupCallState.State.RECIPIENT_SPEAKING
                    && callStateSubject.value?.lastSpeaker?.id == recipientInfo.id) {
                    callStateSubject.onNext(GroupCallState.stoppedSpeaking(recipientInfo))
                }
                peer.release()
            }
        }
    }

    override fun onUserTalking(recipient: CallUserInfo, isSpeaking: Boolean) {
        workerHandler.post {
            Log.e("my_peer", "onUserTalking ${recipient.id}, $isSpeaking")
            if (isSpeaking) {
                if (shouldAcceptUserStartSpeaking(recipient)) {
                    callStateSubject.onNext(GroupCallState.awaitingOffer(recipient))
                }
            } else {
                if (shouldAcceptUserStopSpeaking(recipient)) {
                    releasePeers(recipient)
                }
            }
        }
    }

    override fun onOffer(offer: MessageOffer) {
        workerHandler.post {
            val callInfo = groupCallInfo
            callInfo?:return@post
            Log.e("my_peer", "onOffer ${offer.sessionId}, ${shouldAcceptOffer(offer)}")
            if (shouldAcceptOffer(offer)) {
                signalingManager.getGroupMembers(callInfo).also { members ->
                    members.forEach { member ->
                        if (member.id == offer.data.from) { //only for initiator
                            Log.w(TAG, "ON_OFFER ${offer.data.from}->${offer.data.to} ${offer.data.description}")
                            callStateSubject.onNext(GroupCallState.recipientSpeaking(member))
                            initInternal()
                            val myPeer = MyPeerConnection(member, false, offer.sessionId)
                            peerConnectionMap[member.id] = myPeer
                            myPeer.setOffer(offer, offer.sessionId)

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
            Log.e("my_peer", "onAnswer ${answer.sessionId}, ${shouldAcceptAnswer(answer)}")
            if (shouldAcceptAnswer(answer)) {
                val callInfo = groupCallInfo
                callInfo?:return@post
                peerConnectionMap[answer.data.from]?.also { peer ->
                    Log.w(TAG, "ON_ANSWER ${answer.data.from}->${answer.data.to} ${answer.data.description}")
                    peer.setAnswer(answer, answer.sessionId)
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
                    peer.addIceCandidate(ice, ice.sessionId)
                }
            }
        }
    }







    private fun shouldAcceptUserStartSpeaking(recipient: CallUserInfo): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        callStateSubject.value?.state?.also { state ->
            if (callInfo.me.id != recipient.id && state >= GroupCallState.State.NONE) {
                return true
            }
        }

        return false
    }

    private fun shouldAcceptUserStopSpeaking(recipient: CallUserInfo): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        callStateSubject.value?.state?.also { state ->
            if (callInfo.me.id != recipient.id
                && state >= GroupCallState.State.AWAITING_OFFER
                && callStateSubject.value?.lastSpeaker?.id == recipient.id) {
                return true
            }
        }

        return false
    }

    private fun shouldAcceptOffer(offer: MessageOffer): Boolean {
        val callInfo = groupCallInfo
        callInfo?:return false

        callStateSubject.value?.state?.also { state ->
            if (callInfo.me.id == offer.data.to
                && state == GroupCallState.State.AWAITING_OFFER
                && callStateSubject.value?.lastSpeaker?.id == offer.data.from) {
                return true
            }
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

        callStateSubject.value?.state?.also { state ->
            if (callInfo.me.id == ice.data.to && state >= GroupCallState.State.AWAITING_OFFER) {
                return peerConnectionMap.contains(ice.data.from)
            }
        }

        return false
    }










    @WorkerThread
    private fun emitIceCandidate(userInfo: CallUserInfo, ice: IceCandidate, sessionId: String) {
        workerHandler.post {
            try {
                groupCallInfo?.also { callInfo ->
                    Log.v(TAG, "emitIceCandidate ${userInfo.id}")
                    signalingManager.emitIceCandidate(callInfo, userInfo, ice, sessionId)
                }
            } catch (exc: Exception) {
                onError("Failed emitting ice candidate", exc)
            }
        }
    }

    @WorkerThread
    private fun emitOffer(userInfo: CallUserInfo, offer: SessionDescription, sessionId: String) {
        workerHandler.post {
            try {
                groupCallInfo?.also { callInfo ->
                    Log.v(TAG, "emitOffer ${userInfo.id}")
                    signalingManager.emitOffer(callInfo, userInfo, offer, sessionId)
                }
            } catch (exc: Exception) {
                onError("Failed sending offer", exc)
            }
        }
    }

    @WorkerThread
    private fun emitAnswer(userInfo: CallUserInfo, answer: SessionDescription, sessionId: String) {
        workerHandler.post {
            try {
                groupCallInfo?.also { callInfo ->
                    Log.v(TAG, "emitAnswer ${userInfo.id}")
                    signalingManager.emitAnswer(callInfo, userInfo, answer, sessionId)
                }
            } catch (exc: Exception) {
                onError("Failed sending answer", exc)
            }
        }
    }












    override fun onError(message: String?, exception: Exception?, leave: Boolean, disconnect: Boolean) {
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
    }

    fun release() {
        workerHandler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
        try {
            if (callStateSubject.value?.state == GroupCallState.State.ME_SPEAKING) {
                groupCallInfo?.also { callInfo ->
                    signalingManager.emitStopTalking(callInfo.me)
                }
            }
            recipientAddedSubject.onComplete()
            recipientRemovedSubject.onComplete()
            callStateSubject.onComplete()
            recipientsSubject.onComplete()

            signalingManager.disconnect()
            releasePeers()
        } catch (exc: Throwable) {
            Log.e(TAG, "error", exc)
        }
    }

    companion object {
        const val TAG = "WebRTCManager"
    }

    interface Callback {
        fun onLeave(message: String? = null)

    }











    inner class MyPeerConnection(private val userInfo: CallUserInfo, private val enableOutput: Boolean, private val sessionId: String) {

        private lateinit var localPeer: PeerConnection

        init {
            createPeerConnection()
        }

        private fun createPeerConnection() {
            localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    Log.v("my_peer", "$sessionId, ${userInfo.id} iceCandidateCreated: ${localPeer.signalingState()}")
                    emitIceCandidate(userInfo, iceCandidate, this@MyPeerConnection.sessionId)
                }
            })!!

            Log.e("my_peer", "$sessionId, ${userInfo.id} createPeerConnection: ${localPeer.signalingState()}")

//            localPeer.setBitrate(10000, 11000, 12000)

            if (enableOutput) {
                val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
                val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
                localAudioTrack.setVolume(10.0)

                val stream = peerConnectionFactory.createLocalMediaStream("102")
                stream.addTrack(localAudioTrack)
                localPeer.addStream(stream)
            }
        }

        fun createOffer(sessionId: String) {
            Log.v("my_peer", "$sessionId, ${userInfo.id} createOffer: ${localPeer.signalingState()}")
            if (this.sessionId == sessionId) {
                val sdpConstraints = MediaConstraints()
                sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                localPeer.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        Log.v("my_peer", "$sessionId, ${userInfo.id} createOfferSuccess: ${localPeer.signalingState()}")
                        localPeer.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                        emitOffer(userInfo, sessionDescription, this@MyPeerConnection.sessionId)
                    }

                    override fun onCreateFailure(s: String) {
                        onError(s)
                    }

                    override fun onSetFailure(s: String) {}
                    override fun onSetSuccess() {}
                }, sdpConstraints)
            }
        }

        fun createAnswer(sessionId: String) {
            Log.v("my_peer", "$sessionId, ${userInfo.id} createAnswer: ${localPeer.signalingState()}")
            if (localPeer.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER && this.sessionId == sessionId) {
                localPeer.createAnswer(object : SdpObserver {

                    override fun onCreateSuccess(sdp: SessionDescription) {
                        Log.v("my_peer", "$sessionId, ${userInfo.id} createAnswerSuccess: ${localPeer.signalingState()}")
                        localPeer.setLocalDescription(CustomSdpObserver("localSetLocal"), sdp)
                        emitAnswer(userInfo, sdp, this@MyPeerConnection.sessionId)
                    }

                    override fun onCreateFailure(s: String?) {
                        onError(s)
                    }

                    override fun onSetFailure(s: String?) {}
                    override fun onSetSuccess() {}
                }, MediaConstraints())
            }
        }










        fun addIceCandidate(ice: MessageIceCandidate, sessionId: String) {
            Log.w("my_peer", "$sessionId, ${userInfo.id} addIceCandidate: ${localPeer.signalingState()}")
            if (this.sessionId == sessionId) {
                ice.data.candidate?.also { candidate ->
                    localPeer.addIceCandidate(IceCandidate(candidate.sdpMid, ice.data.candidate.sdpMLineIndex, candidate.candidate))
                    Log.w("my_peer", "$sessionId, ${userInfo.id} addIceCandidate2: ${localPeer.signalingState()}")
                }
            }
        }

        fun setOffer(offer: MessageOffer, sessionId: String) {
            Log.w(TAG, "ON_OFFER setOffer")
            Log.d("my_peer", "$sessionId, ${userInfo.id} setOffer: ${localPeer.signalingState()}")
            if (localPeer.signalingState() == PeerConnection.SignalingState.STABLE && this.sessionId == sessionId) {
                localPeer.setRemoteDescription(object : SdpObserver {
                    override fun onSetFailure(s: String?) {
                        onError(s)
                    }

                    override fun onSetSuccess() {
                        Log.d("my_peer", "$sessionId, ${userInfo.id} setOfferSuccess: ${localPeer.signalingState()}")
                        workerHandler.post {
                            try {
                                createAnswer(this@MyPeerConnection.sessionId)
                            } catch (exc: Exception) {
                                onError("Failed creating answer", exc)
                            }
                        }
                    }

                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(s: String?) {}

                }, SessionDescription(SessionDescription.Type.OFFER, offer.data.description.sdp))
                Log.d("my_peer", "$sessionId, ${userInfo.id} setOffer2: ${localPeer.signalingState()}")
            }
        }

        fun setAnswer(answer: MessageAnswer, sessionId: String) {
            Log.d("my_peer", "$sessionId, ${userInfo.id} setAnswer: ${localPeer.signalingState()}")
            if (localPeer.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER && this.sessionId == sessionId) {
                localPeer.setRemoteDescription(object : SdpObserver {
                    override fun onSetFailure(s: String?) {
                        onError(s)
                    }

                    override fun onSetSuccess() {
                        Log.d("my_peer", "$sessionId, ${userInfo.id} setAnswerSuccess: ${localPeer.signalingState()}")
                        workerHandler.post {

                        }
                    }

                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(s: String?) {}
                }, SessionDescription(SessionDescription.Type.fromCanonicalForm(answer.type.toLowerCase()), answer.data.description.sdp))
                Log.d("my_peer", "$sessionId, ${userInfo.id} setAnswer2: ${localPeer.signalingState()}")
            }
        }

        fun release() {
            Log.e(TAG, "release ${userInfo.id}")
            Log.e("my_peer", "release ${userInfo.id}: ${localPeer.signalingState()}, $sessionId")
            try {
                localPeer.close()
            } catch (exc: Throwable) {
                Log.e("my_peer", "error while release peer", exc)
            }
        }
    }
}