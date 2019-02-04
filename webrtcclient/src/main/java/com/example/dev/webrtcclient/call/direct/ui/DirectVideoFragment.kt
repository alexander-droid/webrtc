package com.example.dev.webrtcclient.call.direct.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.dev.webrtcclient.R
import com.example.dev.webrtcclient.call.direct.DirectCallService
import com.example.dev.webrtcclient.log.LogAdapter
import com.example.dev.webrtcclient.model.CallState
import com.example.dev.webrtcclient.model.CallViewState
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_direct_video.*

import org.webrtc.EglBase
import org.webrtc.VideoTrack

class DirectVideoFragment : Fragment() {

    private lateinit var logAdapter: LogAdapter

    private var disposable: CompositeDisposable? = null

    private var directCallService: DirectCallService? = null

    private var isVideoInitialized = false

    private var rootEglBase: EglBase? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.v(TAG, "onServiceDisconnected")
            logAdapter.clear()
            activity?.finish()
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as DirectCallService.CallBinder).getService()
            directCallService = service
            Log.v(TAG, "onServiceConnected")
            disposable?.add(
                    service.rootEglBaseObserver
                            .subscribe { rootEglBase ->
                                this@DirectVideoFragment.rootEglBase = rootEglBase
                                if (!isVideoInitialized) {
                                    fullscreen_video_view.init(rootEglBase.eglBaseContext, null)
                                    pip_video_view.init(rootEglBase.eglBaseContext, null)
                                    fullscreen_video_view.setZOrderMediaOverlay(true)
                                    pip_video_view.setZOrderMediaOverlay(true)
                                    pip_video_view.setMirror(true)
                                    fullscreen_video_view.setMirror(true)

                                    isVideoInitialized = true
                                }
                            }
            )

            disposable?.add(
                    service.localVideoTrackObserver
                            .subscribe { localVideoTrack ->
                                Log.e(TAG, "localVideoTrack")
                                this@DirectVideoFragment.localVideoTrack = localVideoTrack
                                localVideoTrack?.addSink(pip_video_view)
                                pip_video_view.setZOrderOnTop(true)
                            }
            )

            disposable?.add(
                    service.remoteVideoTrackObserver
                            .subscribe { remoteVideoTrack ->
                                Log.e(TAG, "remoteVideoTrack")
                                this@DirectVideoFragment.remoteVideoTrack = remoteVideoTrack
                                remoteVideoTrack?.addSink(fullscreen_video_view)
                                fullscreen_video_view.setZOrderOnTop(false)
                            }
            )




            disposable?.add(
                    service.callViewStateObservable
                            .subscribe { callState ->
                                Log.d(TAG, "applyCallState $callState")
                                when(callState) {
                                    CallViewState.CALLING_IN -> applyCallingIn()
                                    CallViewState.CALLING_OUT -> applyCallingOut()
                                    CallViewState.CALL_RUNNING -> applyCallRunning()
                                }
                            }
            )

            disposable?.add(
                    service.userInfoObservable
                            .subscribe { userInfo ->
                                userInfoContainer.visibility = View.VISIBLE
                                opponentNameTextView.text = userInfo.id
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
        return inflater.inflate(R.layout.fragment_direct_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = logAdapter

        fullscreen_video_view.visibility = View.GONE
        pip_video_view.visibility = View.GONE

//        pip_video_view.setZOrderOnTop(true)
//        fullscreen_video_view.setZOrderOnTop(false)

        button_answer.setOnClickListener {
            directCallService?.onAnswerClicked()
        }

        button_hangup.setOnClickListener {
            directCallService?.onHangUpClicked()
        }

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        disposable = CompositeDisposable()

        context?.bindService(Intent(context, DirectCallService::class.java), serviceConnection, Context.BIND_IMPORTANT)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause ${activity?.isFinishing}")
        disposable?.dispose()
        disposable = null
        context?.unbindService(serviceConnection)

        localVideoTrack?.removeSink(pip_video_view)
        remoteVideoTrack?.removeSink(fullscreen_video_view)

        rootEglBase = null
        localVideoTrack = null
        remoteVideoTrack = null

        if (activity?.isFinishing == true) {
            fullscreen_video_view.release()
            pip_video_view.release()
        }
    }






    private fun applyCallRunning() {
        callStatusTextView.visibility = View.GONE

        button_answer.visibility = View.GONE
        button_hangup.visibility = View.VISIBLE

        fullscreen_video_view.visibility = View.VISIBLE
        pip_video_view.visibility = View.VISIBLE
    }

    private fun applyCallingIn() {
        callStatusTextView.visibility = View.VISIBLE
        callStatusTextView.text = "Incoming call:"

        button_answer.visibility = View.VISIBLE
        button_hangup.visibility = View.GONE
    }

    private fun applyCallingOut() {
        callStatusTextView.visibility = View.VISIBLE
        callStatusTextView.text = "Calling:"

        button_answer.visibility = View.GONE
        button_hangup.visibility = View.VISIBLE

        fullscreen_video_view.visibility = View.VISIBLE
        pip_video_view.visibility = View.GONE
    }

    companion object {
        private const val TAG = "DirectCallActivity"
    }
}