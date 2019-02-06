package com.example.dev.webrtcclient.model

data class CallInfo(
    val callType: String,
    val channelName: String,
    val me: CallUserInfo,
    val recipient: CallUserInfo
)