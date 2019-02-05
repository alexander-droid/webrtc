package com.example.dev.webrtcclient.call.ptt

import com.example.dev.webrtcclient.BaseSignallingManager

class PTTSignallingManager(var callback: Callback): BaseSignallingManager() {


    interface Callback {
        fun onError(message: String?, exception: Exception? = null)
    }
}