package com.example.dev.webrtcclient.call.ptt

import com.example.dev.webrtcclient.BaseSignallingManager
import com.example.dev.webrtcclient.model.GroupCallInfo

class PTTSignallingManager(var callback: Callback): BaseSignallingManager() {

    fun getGroupMembers(): MutableList<GroupCallInfo.UserInfo> {

    }


    interface Callback {
        fun onError(message: String?, exception: Exception? = null)
    }
}