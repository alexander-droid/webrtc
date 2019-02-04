package com.example.dev.webrtcclient.model.request

data class RequestCall(
    val data: Data,
    val event: String,
    val to: List<String>
) {
    data class Data(
            val caller: String,
            val channel: String,
            val time: Long,
            val type: String
    )
}