package com.example.dev.webrtcclient.model.message

data class MessageDecline(
    val time: Long,
    val channel: String,
    val busy: Boolean
)