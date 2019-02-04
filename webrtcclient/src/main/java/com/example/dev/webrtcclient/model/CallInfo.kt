package com.example.dev.webrtcclient.model

data class CallInfo(
    val callType: String,
    val myId: String,
    val myName: String,
    val opponentId: String,
    val opponentName: String,
    val channelName: String
)