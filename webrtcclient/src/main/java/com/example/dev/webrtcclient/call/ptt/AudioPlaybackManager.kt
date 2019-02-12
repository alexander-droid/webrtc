package com.example.dev.webrtcclient.call.ptt

import android.content.Context
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import android.media.AudioTrack.MODE_STREAM
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioFormat.CHANNEL_CONFIGURATION_MONO
import android.R
import android.media.*
import java.io.File
import java.io.IOException
import android.media.AudioFormat.ENCODING_PCM_8BIT
import android.media.AudioFormat.CHANNEL_CONFIGURATION_MONO
import android.media.AudioTrack.MODE_STREAM
import android.media.AudioFormat.ENCODING_PCM_8BIT
import android.media.AudioFormat.CHANNEL_CONFIGURATION_MONO
import android.media.AudioManager
import java.io.FileInputStream
import java.io.FileNotFoundException


data class AudioPlaybackMessageItem(
    var state: State,
    var currentTime: Int,
    val totalTime: Int
) {

    enum class State {
        PLAYING, PAUSED, STOPPED
    }
}

data class AudioMessageItem(
    val audioUrl: String,
    val position: Int
)

class AudioProgressItem(
    val progress: Int
)

class AudioPlaybackManager(private val context: Context) : MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    private var mediaPlayer: MediaPlayer? = null

    private var currentAudioItem: AudioMessageItem? = null

    private var audioTimerDisposable: Disposable? = null

    private val audioTimerObservable = Observable.interval(200, TimeUnit.MILLISECONDS)

    private var state: AudioPlaybackMessageItem.State =
        AudioPlaybackMessageItem.State.STOPPED

    private var isPreparing = false

    val playback = BehaviorSubject.createDefault(
        AudioPlaybackMessageItem(
            state = state,
            currentTime = 0,
            totalTime = 0
        )
    )

    fun processEvent(audioMessageItem: AudioMessageItem) {
        Log.e(TAG, "play")
        val current = this.currentAudioItem
        this.currentAudioItem = audioMessageItem
        if (current?.audioUrl == audioMessageItem.audioUrl) {
            if (!isPreparing) {
                when(state) {
                    AudioPlaybackMessageItem.State.PLAYING -> {
                        pause()
                    }
                    AudioPlaybackMessageItem.State.PAUSED -> {
                        resume()
                    }
                    AudioPlaybackMessageItem.State.STOPPED -> {
                        prepare(audioMessageItem)
                    }
                }
            }
        } else {
            prepare(audioMessageItem)
        }
    }

    fun seekTo(audioProgressItem: AudioProgressItem) {
        Log.d(TAG, "seekTo")
        mediaPlayer?.also { player ->
            currentAudioItem?.also { current ->
                player.seekTo(audioProgressItem.progress)
                Log.d(TAG, "onNext ${audioProgressItem.progress}")
                playback.onNext(
                    AudioPlaybackMessageItem(
                        state = state,
                        currentTime = audioProgressItem.progress,
                        totalTime = player.duration
                    )
                )
            }
        }
    }

    private fun prepare(audioItem: AudioMessageItem) {
        Log.d(TAG, "prepare ${audioItem.audioUrl}")

        Thread {
            playShortAudioFileViaAudioTrack(audioItem.audioUrl)
        }.start()

//        isPreparing = true
//        audioTimerDisposable?.dispose()
//        mediaPlayer?.release()
//        mediaPlayer = MediaPlayer().apply {
//            setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
//            setDataSource(audioItem.audioUrl)
//            setOnPreparedListener(this@AudioPlaybackManager)
//            setOnCompletionListener(this@AudioPlaybackManager)
//        }
//
//        mediaPlayer?.prepareAsync()
    }

    private fun playShortAudioFileViaAudioTrack(filePath: String?) {
        // We keep temporarily filePath globally as we have only two sample sounds now..
        if (filePath == null)
            return

        //Reading the file..
        var byteData: ByteArray? = null
        var file: File? = null
        file = File(filePath) // for ex. path= "/sdcard/samplesound.pcm" or "/sdcard/samplesound.wav"
        byteData = ByteArray(file.length().toInt())
        var `in`: FileInputStream? = null
        try {
            `in` = FileInputStream(file)
            `in`!!.read(byteData)
            `in`!!.close()

        } catch (e: FileNotFoundException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        // Set and push to audio track..
        val intSize = android.media.AudioTrack.getMinBufferSize(
            8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_8BIT
        )
        val at = AudioTrack(
            AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_8BIT, intSize, AudioTrack.MODE_STREAM
        )
        if (at != null) {
            at.play()
            // Write the byte array to the track
            at.write(byteData, 0, byteData.size)
            at.stop()
            at.release()
        } else
            Log.d("TCAudio", "audio track is not initialised ")

    }

    override fun onPrepared(player: MediaPlayer) {
        isPreparing = false
        start()
    }

    private fun start() {
        Log.d(TAG, "start")
        mediaPlayer?.also { player ->
            currentAudioItem?.also { current ->
                player.seekTo(current.position)
                state = AudioPlaybackMessageItem.State.PLAYING
                playback.onNext(
                    AudioPlaybackMessageItem(
                        state = state,
                        currentTime = player.currentPosition,
                        totalTime = player.duration
                    )
                )
                player.start()
                registerProgressObserver()
            }
        }
    }

    private fun pause() {
        Log.d(TAG, "pause")
        audioTimerDisposable?.dispose()
        mediaPlayer?.also { player ->
            currentAudioItem?.also { current ->
                state = AudioPlaybackMessageItem.State.PAUSED
                player.pause()
                playback.onNext(
                    AudioPlaybackMessageItem(
                        state = state,
                        currentTime = player.currentPosition,
                        totalTime = player.duration
                    )
                )
            }
        }
    }

    private fun resume() {
        Log.d(TAG, "resume")
        mediaPlayer?.also { player ->
            currentAudioItem?.also { current ->
                state = AudioPlaybackMessageItem.State.PLAYING
                player.start()
                registerProgressObserver()
                playback.onNext(
                    AudioPlaybackMessageItem(
                        state = state,
                        currentTime = player.currentPosition,
                        totalTime = player.duration
                    )
                )
            }
        }
    }

    private fun registerProgressObserver() {
        mediaPlayer?.also { player ->
            audioTimerDisposable?.dispose()
            audioTimerDisposable = audioTimerObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    currentAudioItem?.also { current ->
                        playback.onNext(
                            AudioPlaybackMessageItem(
                                state = AudioPlaybackMessageItem.State.PLAYING,
                                currentTime = player.currentPosition,
                                totalTime = player.duration
                            )
                        )
                    }
                }
        }
    }

    override fun onCompletion(player: MediaPlayer) {
        Log.d(TAG, "onCompletion")
        state = AudioPlaybackMessageItem.State.STOPPED
        val duration = try {
            player.duration
        } catch (exc: Exception) {
            Log.e(TAG, "error", exc)
            0
        }
        currentAudioItem?.also { current ->
            playback.onNext(
                AudioPlaybackMessageItem(
                    state = AudioPlaybackMessageItem.State.STOPPED,
                    currentTime = 0,
                    totalTime = duration
                )
            )
        }
        audioTimerDisposable?.dispose()
    }

    fun release() {
        Log.d(TAG, "release")
        mediaPlayer?.also { player ->
            if (state != AudioPlaybackMessageItem.State.STOPPED) {
                onCompletion(player)
            }

            mediaPlayer?.release()
        }
    }



    companion object {
        private const val TAG = "AudioPlaybackManager"
    }
}