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
import com.example.dev.webrtcclient.model.GroupCallState
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
                        showMessage(message ="${userInfo.name} left", bg = ContextCompat.getColor(this@PTTCallActivity, R.color.orange), anchor = button_leave)
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

            disposable?.add(
                service.callStateObservable
                    .subscribe { state ->
                        when(state.state) {
                            GroupCallState.State.NONE -> {
                                talkingTextView.visibility = View.GONE
                                talkingTextView.text = ""
                                audioToggle.isChecked = false
                            }
                            GroupCallState.State.ME_SPEAKING -> {
                                talkingTextView.visibility = View.VISIBLE
                                talkingTextView.text = "You are speaking"
                                audioToggle.isChecked = true
                            }
                            GroupCallState.State.AWAITING_OFFER,
                            GroupCallState.State.RECIPIENT_SPEAKING -> {
                                talkingTextView.visibility = View.VISIBLE
                                talkingTextView.text = "${state.lastSpeaker?.name} is speaking"
                                audioToggle.isChecked = false
                            }
                        }
                    }
            )

            disposable?.add(
                service.messageObservable
                    .subscribe {
                        showMessage(it, anchor = button_leave)
                    }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pttcall)

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
