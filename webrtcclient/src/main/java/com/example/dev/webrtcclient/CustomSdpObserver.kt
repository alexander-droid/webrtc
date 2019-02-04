package com.example.dev.webrtcclient

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class CustomSdpObserver(logTag: String) : SdpObserver {


    private var tag: String? = null

    init {
        tag = this.javaClass.canonicalName
        this.tag = this.tag + " " + logTag
    }


    override fun onCreateSuccess(sessionDescription: SessionDescription) {
        Log.v(tag, "onCreateSuccess() called with: sessionDescription = [$sessionDescription]")
    }

    override fun onSetSuccess() {
        Log.v(tag, "onSetSuccess() called")
    }

    override fun onCreateFailure(s: String) {
        Log.e(tag, "onCreateFailure() called with: s = [$s]")
    }

    override fun onSetFailure(s: String) {
        Log.e(tag, "onSetFailure() called with: s = [$s]")
    }

}
