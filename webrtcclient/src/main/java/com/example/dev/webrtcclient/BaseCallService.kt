package com.example.dev.webrtcclient

import android.app.Service
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

abstract class BaseCallService: Service() {

    protected lateinit var audioManager: AudioManager

    private lateinit var focusRequest: AudioFocusRequest

    private var wasMicrophoneMute = false
    private var wasSpeakerphoneOn = false
    private var prevAudioMode = 0

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        var typeOfChange = "AUDIOFOCUS_NOT_DEFINED"
        typeOfChange = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
            AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
            else -> "AUDIOFOCUS_INVALID"
        }
        Log.e(TAG, "onAudioFocusChange: $typeOfChange")
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        wasMicrophoneMute = audioManager.isMicrophoneMute
        wasSpeakerphoneOn = audioManager.isMicrophoneMute
        prevAudioMode = audioManager.mode

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                build()
            }
            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.mode = prevAudioMode
        audioManager.isMicrophoneMute = wasMicrophoneMute
        audioManager.isSpeakerphoneOn = wasSpeakerphoneOn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest)
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    companion object {
        const val TAG = "CallService"
    }
}