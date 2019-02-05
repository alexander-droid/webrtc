package com.example.dev.webrtcclient.call.direct

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.support.annotation.WorkerThread
import android.util.Base64
import android.util.Log
import com.example.dev.webrtcclient.BaseWebRTCManager
import com.example.dev.webrtcclient.CustomPeerConnectionObserver
import com.example.dev.webrtcclient.CustomSdpObserver
import com.example.dev.webrtcclient.api.XirsysApi
import com.example.dev.webrtcclient.log.SimpleEvent
import com.example.dev.webrtcclient.model.CallInfo
import com.example.dev.webrtcclient.model.CallState
import com.example.dev.webrtcclient.model.CallUserInfo
import com.example.dev.webrtcclient.model.message.MessageAnswer
import com.example.dev.webrtcclient.model.message.MessageDecline
import com.example.dev.webrtcclient.model.message.MessageIceCandidate
import com.example.dev.webrtcclient.model.message.MessageOffer
import com.example.dev.webrtcclient.model.response.TurnServer
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import okhttp3.OkHttpClient
import org.webrtc.*
import org.webrtc.voiceengine.WebRtcAudioUtils
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class DirectWebRTCManager(
    private val context: Context,
    private val callback: Callback
) : BaseWebRTCManager(), DirectSignallingManager.Callback {

    private val timeout = 60000L
    private val timeoutHandler = Handler()

    private val rootEglSubject = BehaviorSubject.create<EglBase>()
    val rootEglBaseObserver: Observable<EglBase>
        get() = rootEglSubject.observeOn(AndroidSchedulers.mainThread())

    private val localVideoTrackSubject = BehaviorSubject.create<VideoTrack>()
    val localVideoTrackObserver: Observable<VideoTrack>
        get() = localVideoTrackSubject.observeOn(AndroidSchedulers.mainThread())

    private val remoteVideoTrackSubject = BehaviorSubject.create<VideoTrack>()
    val remoteVideoTrackObserver: Observable<VideoTrack>
        get() = remoteVideoTrackSubject.observeOn(AndroidSchedulers.mainThread())

    private val callStateSubject = BehaviorSubject.createDefault(CallState.NONE)
    val callStateObservable: Observable<CallState>
        get() = callStateSubject.observeOn(AndroidSchedulers.mainThread())



    val logList = mutableListOf<SimpleEvent>()
    private val logSubject = PublishSubject.create<SimpleEvent>()
    val logObservable: Observable<SimpleEvent>
        get() = logSubject.observeOn(AndroidSchedulers.mainThread())


    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerThread = HandlerThread("workerHandlerThread")
    private var workerHandler: Handler
    private var disposable = CompositeDisposable()


    private val peerIceServers = mutableListOf<PeerConnection.IceServer>()
    private lateinit var rootEglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var localPeer: PeerConnection

    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    private lateinit var audioConstraints: MediaConstraints

    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoConstraints: MediaConstraints? = null

    private val signalingManager = DirectSignallingManager(this)

    private var withVideo = false
    private lateinit var callInfo: CallInfo

    init {

        handlerThread.start()
        workerHandler = Handler(handlerThread.looper)
    }

    fun enableAudio(isEnabled: Boolean) {
        localAudioTrack.setEnabled(isEnabled)
    }

    private fun initInternal() {
        rootEglBase = EglBase.create()
        rootEglSubject.onNext(rootEglBase)

        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        audioConstraints = MediaConstraints()

        val peerConnectionBuilder = PeerConnectionFactory.builder()
        if (withVideo) {
            videoConstraints = MediaConstraints()
            val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
                rootEglBase.eglBaseContext, true, true)
            val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

            peerConnectionBuilder.setVideoDecoderFactory(defaultVideoDecoderFactory)
            peerConnectionBuilder.setVideoEncoderFactory(defaultVideoEncoderFactory)
        }

        peerConnectionFactory = peerConnectionBuilder.createPeerConnectionFactory()

        if (withVideo) {
            val videoCapturerAndroid = createCameraCapturer(Camera2Enumerator(context))
            val source = peerConnectionFactory.createVideoSource(false)
            videoSource = source
            videoCapturerAndroid?.initialize(
                SurfaceTextureHelper.create("VideoCapturerThread1", rootEglBase.eglBaseContext),
                context,
                source.capturerObserver
            )
            val localTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
            localVideoTrack = localTrack
            videoCapturerAndroid?.startCapture(1024, 768, 30)
            localVideoTrackSubject.onNext(localTrack)
        }

        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(peerIceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.

        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, object : CustomPeerConnectionObserver("localPeerCreation") {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                super.onIceCandidate(iceCandidate)
                workerHandler.post {
                    try {
                        addEvent(SimpleEvent.OutMessage("Emit ice candidate"))
                        emitIceCandidate(iceCandidate)
                    } catch (exc: Exception) {
                        onError("Failed emitting ice candidate", exc)
                    }
                }
            }

            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
                mainHandler.post {
                    gotRemoteStream(mediaStream)
                }
            }
        })!!

        addStreamToLocalPeer()
    }

    private fun addStreamToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        localVideoTrack?.also {
            stream.addTrack(it)
        }
        localPeer.addStream(stream)
    }

    private fun gotRemoteStream(stream: MediaStream) {
        if (withVideo) {
            val videoTrack = stream.videoTracks[0]
            remoteVideoTrackSubject.onNext(videoTrack)
        }
    }

    private fun setIceServers(turnServer: TurnServer) {
        addEvent(SimpleEvent.InMessage("Set ice servers"))
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

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }








    private fun createOffer() {
        addEvent(SimpleEvent.InternalMessage("create offer"))
        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (withVideo) {
            sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        localPeer.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                workerHandler.post {
                    try {
                        localPeer.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                        setState(CallState.AWAITING_ANSWER)
                        addEvent(SimpleEvent.OutMessage("Emit offer"))
                        emitOffer(sessionDescription)
                    } catch (exc: Exception) {
                        onError("Failed sending offer", exc)
                    }
                }
            }

            override fun onCreateFailure(s: String) {
                onError(s)
            }

            override fun onSetFailure(s: String) {}
            override fun onSetSuccess() {}
        }, sdpConstraints)
    }

    private fun createAnswer(offer: MessageOffer) {
        addEvent(SimpleEvent.InternalMessage("create answer"))
        localPeer.createAnswer(object : SdpObserver {

            override fun onCreateSuccess(sdp: SessionDescription) {
                workerHandler.post {
                    try {
                        localPeer.setLocalDescription(CustomSdpObserver("localSetLocal"), sdp)
                        addEvent(SimpleEvent.OutMessage("Emit answer"))
                        emitAnswer(sdp, offer)
                        setState(CallState.CALL_RUNNING)
                    } catch (exc: Exception) {
                        onError("Failed sending answer", exc)
                    }
                }
            }

            override fun onCreateFailure(s: String?) {
                onError(s)
            }

            override fun onSetFailure(s: String?) {}
            override fun onSetSuccess() {}
        }, MediaConstraints())
    }








    private fun addIceCandidate(ice: MessageIceCandidate) {
        ice.data.candidate?.also { candidate ->
            localPeer.addIceCandidate(IceCandidate(candidate.sdpMid, ice.data.candidate.sdpMLineIndex, candidate.candidate))
        }
    }

    private fun setOffer(offer: MessageOffer) {
        localPeer.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(s: String?) {
                onError(s)
            }

            override fun onSetSuccess() {
                workerHandler.post {
                    try {
                        setState(CallState.CREATING_ANSWER)
                        createAnswer(offer)
                    } catch (exc: Exception) {
                        onError("Failed creating answer", exc)
                    }
                }
            }

            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(s: String?) {}

        }, SessionDescription(SessionDescription.Type.OFFER, offer.data.description.sdp))
    }

    private fun setAnswer(answer: MessageAnswer) {
        localPeer.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(s: String?) {
                onError(s)
            }

            override fun onSetSuccess() {
                workerHandler.post {
                    setState(CallState.CALL_RUNNING)
                }
            }

            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(s: String?) {}
        }, SessionDescription(SessionDescription.Type.fromCanonicalForm(answer.type.toLowerCase()), answer.data.description.sdp))
    }








    //User interaction
    fun doCall(callInfo: CallInfo) {
        workerHandler.post {
            if (callStateSubject.value == CallState.NONE) {
                this.withVideo = callInfo.callType == CALL_TYPE_VIDEO
                this.callInfo = callInfo
                setState(CallState.INITIALIZING_CALLING_OUT)
                setTimeoutHandling()
                try {
                    initInternal()
                    val turnServer = requestIceServers()
                    if (turnServer != null) {
                        setIceServers(turnServer)
                        createPeerConnection()

                        signalingManager.connect(callInfo) {
                            signalingManager.subscribeMyChannel()
                            signalingManager.subscribePresenceChannel()
                            addEvent(SimpleEvent.OutMessage("Emit call attempt"))
                            emitCallAttempt()
                            setState(CallState.CALLING_OUT)
                        }
                    } else {
                        onError( "Failed to receive ice servers")
                    }
                } catch (exc: Exception) {
                    onError( "Failed to init call", exc)
                }
            }
        }
    }

    fun callReceived(callInfo: CallInfo) {
        Log.w("DirectSignallingManager", "callReceived $callInfo")
        Log.w("DirectSignallingManager", "call state: ${callStateSubject.value}")
        workerHandler.post {
            if (callStateSubject.value == CallState.NONE) {
                this.callInfo = callInfo
                this.withVideo = callInfo.callType == CALL_TYPE_VIDEO
                setState(CallState.INITIALIZING_CALLING_IN)

                try {
                    initInternal()
                    val turnServer = requestIceServers()
                    if (turnServer != null) {
                        setIceServers(turnServer)
                        createPeerConnection()

                        signalingManager.connect(callInfo) {
                            signalingManager.subscribeMyChannel()
                            setState(CallState.CALLING_IN)
                            setTimeoutHandling()
                        }
                    } else {
                        onError( "Failed to receive ice servers")
                    }
                } catch (exc: Exception) {
                    onError( "Failed to receive call", exc)
                }

            } else {
                Log.w("DirectSignallingManager", "try emitBusy ${callInfo.opponentId}, ${this.callInfo.opponentId}")
                signalingManager.emitBusy(callInfo)
            }
        }
    }

    fun answerCall() {
        workerHandler.post {
            if (callStateSubject.value == CallState.CALLING_IN) {
                try {
                    signalingManager.subscribePresenceChannel()
                    addEvent(SimpleEvent.InternalMessage("Awaiting offer"))
                    setState(CallState.AWAITING_OFFER)
                } catch (exc: Exception) {
                    onError( "Failed to answer call", exc)
                }
            }
        }
    }

    fun declineCall() {
        workerHandler.post {
            callStateSubject.value?.also { state ->
                try {
                    addEvent(SimpleEvent.OutMessage("Emit decline call"))
                    signalingManager.emitCallDecline()
                    onDecline()
                } catch (exc: Exception) {
                    onError("Failed decline call", exc)
                }
            }
        }
    }








    //Pusher callbacks
    override fun onDecline(decline: MessageDecline?) {
        addEvent(SimpleEvent.InMessage("Decline received"))
        mainHandler.post {
            val message: String = if (decline?.busy == true) {
                "${callInfo.opponentName} is busy"
            } else {
                "Call ended"
            }
            callback.onFinishCall(message)
        }
    }

    override fun onOffer(offer: MessageOffer) {
        addEvent(SimpleEvent.InMessage("Offer received"))
        workerHandler.post {
            if (callStateSubject.value == CallState.AWAITING_OFFER) {
                setState(CallState.SETTING_OFFER)
                setOffer(offer)
            }
        }
    }

    override fun onAnswer(answer: MessageAnswer) {
        addEvent(SimpleEvent.InMessage("Answer received"))
        workerHandler.post {
            if (callStateSubject.value == CallState.AWAITING_ANSWER) {
                setState(CallState.SETTING_ANSWER)
                setAnswer(answer)
            }
        }
    }

    override fun onIce(ice: MessageIceCandidate) {
        addEvent(SimpleEvent.InMessage("Ice candidate received"))
        workerHandler.post {
            addIceCandidate(ice)
        }
    }

    override fun onGenerateOffer() {
        workerHandler.post {
            if (callStateSubject.value == CallState.CALLING_OUT) {
                setState(CallState.CREATING_OFFER)
                createOffer()
            }
        }
    }

    override fun onError(message: String?, exception: Exception?) {
        Log.d(TAG, message, exception)
        addEvent(SimpleEvent.ErrorMessage(message))
        mainHandler.post {
            callback.onFinishCall(message)
        }
    }

    override fun onOpponentUnsubscribed(userInfo: CallUserInfo) {
        callStateSubject.value?.also { state ->
            if (state < CallState.CALL_RUNNING) {
                //TODO
            }
        }
    }







    fun emitIceCandidate(ice: IceCandidate) {
        signalingManager.emitIceCandidate(ice)
    }

    fun emitOffer(offer: SessionDescription) {
        signalingManager.emitOffer(offer)
    }

    fun emitAnswer(answer: SessionDescription, offer: MessageOffer) {
        signalingManager.emitAnswer(answer)
        setState(CallState.CALL_RUNNING)
    }

    private fun emitCallAttempt() {
        signalingManager.emitCallAttempt()
    }








    private fun setState(state: CallState) {
        callStateSubject.onNext(state)
    }

    private fun setTimeoutHandling() {
        timeoutHandler.postDelayed({
            if (callStateSubject.value != CallState.CALL_RUNNING) {
                declineCall()
            }
        }, timeout)
    }


    fun release() {
        logSubject.onComplete()
        callStateSubject.onComplete()
        localVideoTrackSubject.onComplete()
        remoteVideoTrackSubject.onComplete()
        rootEglSubject.onComplete()

        disposable.dispose()
        signalingManager.disconnect()
        localPeer.dispose()
        workerHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        timeoutHandler.removeCallbacksAndMessages(null)
        handlerThread.quit()
//        rootEglBase.release()
//        peerConnectionFactory.dispose()
//        videoSource.dispose()
//        localVideoTrack.dispose()
//        audioSource.dispose()
//        localAudioTrack.dispose()
    }




    private fun addEvent(event: SimpleEvent) {
        logList.add(event)
        logSubject.onNext(event)
    }


    companion object {
        const val TAG = "DirectWebRTCManager"

        const val CALL_TYPE_VIDEO = "video"
        const val CALL_TYPE_VOICE = "voice"
    }


    interface Callback {
        fun onFinishCall(message: String? = null)
    }
}