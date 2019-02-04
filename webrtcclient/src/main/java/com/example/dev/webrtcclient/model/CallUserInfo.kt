package com.example.dev.webrtcclient.model

data class CallUserInfo(
    val id: String,
    val data: Data,
    val isTalking: Boolean = false
) {
    data class Data(
            val name: String
    )
}