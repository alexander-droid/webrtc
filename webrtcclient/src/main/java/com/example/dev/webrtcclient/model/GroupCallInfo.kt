package com.example.dev.webrtcclient.model

data class GroupCallInfo(
    val callType: String,
    val myId: String,
    val myName: String,
    var opponents: MutableList<UserInfo> = mutableListOf(),
    val channelName: String
) {
    data class UserInfo(
        val id: String,
        val name: String
    )
}