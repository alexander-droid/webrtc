package com.example.dev.webrtcclient.model.request

data class RequestDecline(
    val event: String,
    val to: List<String>,
    val data: Data
) {
    data class Data(
        val channel: String,
        val time: Long,
        val busy: Boolean
    )
}