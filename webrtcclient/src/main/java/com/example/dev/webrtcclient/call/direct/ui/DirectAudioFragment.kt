package com.example.dev.webrtcclient.call.direct.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.dev.webrtcclient.R
import com.example.dev.webrtcclient.call.direct.DirectCallService
import com.example.dev.webrtcclient.log.LogAdapter
import com.example.dev.webrtcclient.model.CallState
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_direct_audio.*

class DirectAudioFragment : Fragment() {

    private lateinit var logAdapter: LogAdapter

    private var disposable: CompositeDisposable? = null

    private var callService: DirectCallService? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            activity?.finish()
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as DirectCallService.CallBinder).getService()
            callService = service

            disposable?.add(
                service.callStateObservable
                    .subscribe { callState ->
                        when(callState) {
                            CallState.CALLING_OUT -> applyCallingOut()
                            CallState.CALLING_IN -> applyCallingIn()
                            CallState.CALL_RUNNING -> applyCallRunning()
                            null -> {

                            }
                        }
                    }
            )

            disposable?.add(
                service.userInfoObservable
                    .subscribe { userInfo ->
                        userInfoContainer.visibility = View.VISIBLE
                        opponentNameTextView.text = userInfo.name
                    }
            )

            logAdapter.setAll(service.loglist)
            disposable?.add(
                service.logObservable
                    .subscribe {
                        logAdapter.add(it)
                    }
            )
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_direct_audio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = logAdapter

        button_answer.setOnClickListener {
            callService?.onAnswerClicked()
        }

        button_hangup.setOnClickListener {
            callService?.onHangUpClicked()
        }
    }

    override fun onResume() {
        super.onResume()
        disposable = CompositeDisposable()

        context?.bindService(Intent(context, DirectCallService::class.java), serviceConnection, Context.BIND_IMPORTANT)
    }

    override fun onPause() {
        super.onPause()
        disposable?.dispose()
        disposable = null
        context?.unbindService(serviceConnection)
    }





    private fun applyCallRunning() {
        button_answer.visibility = View.GONE
        button_hangup.visibility = View.VISIBLE
    }

    private fun applyCallingIn() {
        button_answer.visibility = View.VISIBLE
        button_hangup.visibility = View.GONE
    }

    private fun applyCallingOut() {
        button_answer.visibility = View.GONE
        button_hangup.visibility = View.VISIBLE
    }
}
