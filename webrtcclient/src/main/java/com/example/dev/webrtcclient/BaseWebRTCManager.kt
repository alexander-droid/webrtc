package com.example.dev.webrtcclient

import android.support.annotation.WorkerThread
import android.util.Base64
import android.webkit.WebResourceError
import com.example.dev.webrtcclient.api.XirsysApi
import com.example.dev.webrtcclient.model.response.TurnServer
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioTrack
import org.webrtc.voiceengine.WebRtcAudioUtils
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

abstract class BaseWebRTCManager {

    private val xirsysApiManager = Retrofit.Builder()
        .baseUrl("https://global.xirsys.net")
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .client(OkHttpClient.Builder().build())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(XirsysApi::class.java)

    init {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
        WebRtcAudioManager.setStereoInput(true)
        WebRtcAudioManager.setStereoOutput(true)
    }

    @WorkerThread
    protected fun requestIceServers(): TurnServer? {
        val data = "cilicondev:f903b92a-154c-11e9-80fd-0242ac110003".toByteArray(charset("UTF-8"))
        val auth = "Basic " + Base64.encodeToString(data, Base64.NO_WRAP)
        return xirsysApiManager.getIceCandidates(auth).execute().body()
    }
}