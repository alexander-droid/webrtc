package com.example.dev.webrtcclient.call.ptt

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.example.dev.webrtcclient.BaseWebRTCManager
import com.example.dev.webrtcclient.CustomPeerConnectionObserver
import com.example.dev.webrtcclient.CustomSdpObserver
import com.example.dev.webrtcclient.model.GroupCallInfo
import com.example.dev.webrtcclient.model.message.MessageAnswer
import com.example.dev.webrtcclient.model.message.MessageIceCandidate
import com.example.dev.webrtcclient.model.message.MessageOffer
import com.example.dev.webrtcclient.model.response.TurnServer
import org.webrtc.*

class PTTWebRTCManager(
    private val context: Context,
    private val callback: Callback
) : BaseWebRTCManager(), PTTSignallingManager.Callback {

    private var groupCallInfo: GroupCallInfo? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerThread = HandlerThread("workerHandlerThread")
    private var workerHandler: Handler

    private val signalingManager = PTTSignallingManager(this)

    private lateinit var rtcConfig: PeerConnection.RTCConfiguration
    private val peerIceServers = mutableListOf<PeerConnection.IceServer>()
    private lateinit var rootEglBase: EglBase
    lateinit var peerConnectionFactory: PeerConnectionFactory

    private val peerConnectionMap = mutableMapOf<String, MyPeerConnection>()

    init {
        handlerThread.start()
        workerHandler = Handler(handlerThread.looper)

        workerHandler.post {
            try {
                val turnServer = requestIceServers()
                if (turnServer != null) {
                    setIceServers(turnServer)
                } else {
                    onError( "Failed to receive ice servers")
                }
            } catch (exc: Exception) {
                onError( "Failed to receive ice servers", exc)
            }
        }
    }

    private fun initInternal() {
        rootEglBase = EglBase.create()

        //Initialize PeerConnectionFactory globals.
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










    //User interaction
    fun startTalking(callInfo: GroupCallInfo?) {
        workerHandler.post {
            callInfo?:return@post
            initInternal()
            val members = signalingManager.getGroupMembers()
            if (members.isNotEmpty()) {
                callInfo.opponents = members
                createPeerConnections(callInfo)
                this.groupCallInfo = callInfo
            }
        }

    }

    private fun createPeerConnections(groupCallInfo: GroupCallInfo) {
        groupCallInfo.opponents.forEach { userInfo ->
            val myPeer = MyPeerConnection(userInfo)
            peerConnectionMap[userInfo.id] = myPeer
        }

        peerConnectionMap.forEach {
            it.value.createOffer()
        }
    }

    fun stopTalking() {
        this.groupCallInfo = null
    }












    override fun onError(message: String?, exception: Exception?) {
        Log.d(TAG, message, exception)
        mainHandler.post {
            callback.onLeave(message)
        }
    }

    fun release() {
//        disposable.dispose()
        signalingManager.disconnect()
        peerConnectionMap.forEach {
            it.value.release()
        }
        workerHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        handlerThread.quit()
    }

    companion object {
        const val TAG = "PTTWebRTCManager"
    }

    interface Callback {
        fun onLeave(message: String? = null)

    }








    inner class MyPeerConnection(private val userInfo: GroupCallInfo.UserInfo) {

        private lateinit var localPeer: PeerConnection

        init {
            createPeerConnection()
        }

        private fun createPeerConnection() {
            localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    emitIceCandidate(userInfo, iceCandidate)
                }
            })!!

            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

            val stream = peerConnectionFactory.createLocalMediaStream("102")
            stream.addTrack(localAudioTrack)
            localPeer.addStream(stream)
        }

        fun createOffer() {
            val sdpConstraints = MediaConstraints()
            sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            localPeer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    workerHandler.post {
                        try {
                            localPeer.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                            emitOffer(userInfo, sessionDescription)
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

        fun createAnswer() {
            localPeer.createAnswer(object : SdpObserver {

                override fun onCreateSuccess(sdp: SessionDescription) {
                    workerHandler.post {
                        try {
                            localPeer.setLocalDescription(CustomSdpObserver("localSetLocal"), sdp)
                            emitAnswer(userInfo, sdp)
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

        private fun setAnswer(answer: MessageAnswer) {
            localPeer.setRemoteDescription(object : SdpObserver {
                override fun onSetFailure(s: String?) {
                    onError(s)
                }

                override fun onSetSuccess() {
                    workerHandler.post {

                    }
                }

                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(s: String?) {}
            }, SessionDescription(SessionDescription.Type.fromCanonicalForm(answer.type.toLowerCase()), answer.data.description.sdp))
        }

        fun release() {
            localPeer.dispose()
        }
    }
}