package com.example.dev.webrtcclient.model

data class GroupCallInfo(
    val callType: String,
    val channelName: String,
    val me: CallUserInfo,
    var recipients: MutableList<CallUserInfo> = mutableListOf()
)