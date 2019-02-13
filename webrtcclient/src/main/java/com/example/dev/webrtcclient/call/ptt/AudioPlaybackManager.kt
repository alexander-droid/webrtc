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
import android.media.AudioFormat.ENCODING_PCM_8BIT
import android.media.AudioFormat.CHANNEL_CONFIGURATION_MONO
import android.media.AudioTrack.MODE_STREAM
import android.media.AudioFormat.ENCODING_PCM_8BIT
import android.media.AudioFormat.CHANNEL_CONFIGURATION_MONO
import android.media.AudioManager
import java.nio.ByteOrder.LITTLE_ENDIAN
import android.R.attr.order
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder


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

        isPreparing = true
        audioTimerDisposable?.dispose()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(audioItem.audioUrl)
            setOnPreparedListener(this@AudioPlaybackManager)
            setOnCompletionListener(this@AudioPlaybackManager)
        }

        mediaPlayer?.prepareAsync()
    }

    /*@Throws(IOException::class)
    private fun rawToWave(rawFile: File, waveFile: File) {

        val rawData = ByteArray(rawFile.length().toInt())
        var input: DataInputStream? = null
        try {
            input = DataInputStream(FileInputStream(rawFile))
            input!!.read(rawData)
        } finally {
            if (input != null) {
                input!!.close()
            }
        }

        var output: DataOutputStream? = null
        try {
            output = DataOutputStream(FileOutputStream(waveFile))
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(output, "RIFF") // chunk id
            writeInt(output!!, 36 + rawData.size) // chunk size
            writeString(output, "WAVE") // format
            writeString(output, "fmt ") // subchunk 1 id
            writeInt(output!!, 16) // subchunk 1 size
            writeShort(output!!, 1) // audio format (1 = PCM)
            writeShort(output!!, 1) // number of channels
            writeInt(output!!, 44100) // sample rate
            writeInt(output!!, 44100 * 2) // byte rate
            writeShort(output!!, 2) // block align
            writeShort(output!!, 16) // bits per sample
            writeString(output, "data") // subchunk 2 id
            writeInt(output!!, rawData.size) // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            val shorts = ShortArray(rawData.size / 2)
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            val bytes = ByteBuffer.allocate(shorts.size * 2)
            for (s in shorts) {
                bytes.putShort(s)
            }

            output!!.write(fullyReadFileToBytes(rawFile))
        } finally {
            if (output != null) {
                output!!.close()
            }
        }
    }

    @Throws(IOException::class)
    fun fullyReadFileToBytes(f: File): ByteArray {
        val size = f.length().toInt()
        val bytes = ByteArray(size)
        val tmpBuff = ByteArray(size)
        val fis = FileInputStream(f)
        try {

            var read = fis.read(bytes, 0, size)
            if (read < size) {
                var remain = size - read
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain)
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read)
                    remain -= read
                }
            }
        } catch (e: IOException) {
            throw e
        } finally {
            fis.close()
        }

        return bytes
    }

    @Throws(IOException::class)
    private fun writeInt(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
        output.write(value shr 16)
        output.write(value shr 24)
    }

    @Throws(IOException::class)
    private fun writeShort(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
    }

    @Throws(IOException::class)
    private fun writeString(output: DataOutputStream, value: String) {
        for (i in 0 until value.length) {
            output.write(value[i].toInt())
        }
    }*/

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