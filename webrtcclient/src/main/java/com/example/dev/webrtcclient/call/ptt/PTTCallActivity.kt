package com.example.dev.webrtcclient.call.ptt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.example.dev.webrtcclient.BaseActivity
import com.example.dev.webrtcclient.R
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_pttcall.*

class PTTCallActivity : BaseActivity() {

    private var disposable: CompositeDisposable? = null

    private var callService: PTTCallService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            finish()
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as PTTCallService.CallBinder).getService()
            callService = service

            disposable?.add(
                service.recipientAddedObservable
                    .subscribe { userInfo ->
                        showMessage(message ="${userInfo.name} joined", bg = ContextCompat.getColor(this@PTTCallActivity, R.color.blue), anchor = button_leave)
                    }
            )

            disposable?.add(
                service.recipientRemovedObservable
                    .subscribe { userInfo ->
                        showMessage(message ="${userInfo.name} leaved", bg = ContextCompat.getColor(this@PTTCallActivity, R.color.orange), anchor = button_leave)
                    }
            )

            disposable?.add(
                service.userTalkingObservable
                    .subscribe { userInfo ->
                        if (userInfo.isTalking) {
                            talkingTextView.visibility = View.VISIBLE
                            talkingTextView.text = "${userInfo.name} is speaking"
//                            audioBtn.isEnabled = false
                        } else {
                            talkingTextView.visibility = View.GONE
                            talkingTextView.text = ""
//                            audioBtn.isEnabled = true
                        }
                    }
            )

            disposable?.add(
                service.recipientsObservable
                    .subscribe { userInfoList ->
                        loadingView.visibility = View.GONE
                        content.visibility = View.VISIBLE
                        userInfoContainer.visibility = View.VISIBLE
                        userInfoContainer.removeAllViews()
                        if (userInfoList.isEmpty()) {
                            userInfoContainer.addView(TextView(this@PTTCallActivity).apply {
                                text = "Nobody here"
                                gravity = Gravity.CENTER
                            })
                        } else {
                            userInfoList.forEach {
                                userInfoContainer.addView(TextView(this@PTTCallActivity).apply {
                                    text = it.name
                                    gravity = Gravity.CENTER
                                })
                            }
                        }
                    }
            )
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pttcall)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL), 0)

        audioToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                callService?.startTalking()
            } else {
                callService?.stopTalking()
            }
        }

        button_leave.setOnClickListener {
            callService?.leave()
        }
    }

    override fun onResume() {
        super.onResume()
        disposable = CompositeDisposable()
        bindService(Intent(this, PTTCallService::class.java), serviceConnection, Context.BIND_IMPORTANT)
    }

    override fun onPause() {
        super.onPause()
        disposable?.dispose()
        disposable = null
        unbindService(serviceConnection)

    }

    override fun onBackPressed() {

    }

    companion object {

        private const val TAG = "CallActivity"

        fun start(context: Context?) {
            context?.startActivity(Intent(context, PTTCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
