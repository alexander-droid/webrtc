package com.example.dev.webrtcclient.call.ptt

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import com.example.dev.webrtcclient.AudioUtils
import com.example.dev.webrtcclient.R
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.audio_view.view.*
import java.io.File

class AudioView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val onAudioProgressChanged: PublishSubject<AudioProgressItem> = PublishSubject.create()

    val onEventClicked: PublishSubject<AudioMessageItem> = PublishSubject.create()

    init {
        val content = LayoutInflater.from(context).inflate(R.layout.audio_view, null, false)
        addView(content)
    }

    fun subscribe(file: File, audioPlaybackObserver: Observable<AudioPlaybackMessageItem>): Disposable {
        val audioPath = file.absolutePath
        val audioPlaybackDisposable = audioPlaybackObserver
            .subscribe { playbackItem ->
                current_audio_duration.visibility = View.VISIBLE
                total_audio_duration.visibility = View.GONE
                when(playbackItem.state) {
                    AudioPlaybackMessageItem.State.PLAYING -> {
                        play_audio_btn.visibility = View.GONE
                        pause_audio_btn.visibility = View.VISIBLE
                        audio_progress_bar.progress = playbackItem.currentTime
                        current_audio_duration.text = AudioUtils.formatAudioDuration(playbackItem.currentTime.toLong())
                    }
                    AudioPlaybackMessageItem.State.PAUSED,
                    AudioPlaybackMessageItem.State.STOPPED -> {
                        play_audio_btn.visibility = View.VISIBLE
                        pause_audio_btn.visibility = View.GONE
                        audio_progress_bar.progress = playbackItem.currentTime
                        current_audio_duration.text = AudioUtils.formatAudioDuration(playbackItem.currentTime.toLong())
                    }
                }
            }


        play_audio_btn.setOnClickListener {
            onEventClicked.onNext(AudioMessageItem(audioPath, audio_progress_bar.progress))
        }
        pause_audio_btn.setOnClickListener {
            onEventClicked.onNext(AudioMessageItem(audioPath, audio_progress_bar.progress))
        }

        audio_progress_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onAudioProgressChanged.onNext(AudioProgressItem(progress))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val duration = AudioUtils.getDurationFromUrl(file.absolutePath)
        total_audio_duration.text = AudioUtils.formatAudioDuration(duration)
        audio_progress_bar.max = duration.toInt()

        return audioPlaybackDisposable
    }
}