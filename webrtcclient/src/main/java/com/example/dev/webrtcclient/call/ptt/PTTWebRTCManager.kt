package com.example.dev.webrtcclient.call.ptt

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.example.dev.webrtcclient.BaseWebRTCManager
import com.example.dev.webrtcclient.CustomPeerConnectionObserver
import com.example.dev.webrtcclient.log.SimpleEvent
import com.example.dev.webrtcclient.model.response.TurnServer
import org.webrtc.*

class PTTWebRTCManager(
    private val context: Context,
    private val callback: Callback
) : BaseWebRTCManager(), PTTSignallingManager.Callback {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerThread = HandlerThread("workerHandlerThread")
    private var workerHandler: Handler

    private val signalingManager = PTTSignallingManager(this)

    private val peerIceServers = mutableListOf<PeerConnection.IceServer>()
    private lateinit var rootEglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var localPeer: PeerConnection

    private lateinit var localAudioTrack: AudioTrack

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

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        createPeerConnection()
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
//                pusherManager.emitIceCandidate(iceCandidate)
            }
        })!!

        addStreamToLocalPeer()
    }

    private fun addStreamToLocalPeer() {
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        localPeer.addStream(stream)
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

    override fun onError(message: String?, exception: Exception?) {
        Log.d(TAG, message, exception)
        mainHandler.post {
            callback.onLeave(message)
        }
    }

    fun release() {
//        disposable.dispose()
        signalingManager.disconnect()
        localPeer.dispose()
        workerHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        handlerThread.quit()
    }

    companion object {
        const val TAG = "PTTWebRTCManager"
    }

    interface Callback {
        fun onLeave(message: String?)

    }
}