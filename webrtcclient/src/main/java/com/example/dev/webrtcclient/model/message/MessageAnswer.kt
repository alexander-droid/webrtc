package com.example.dev.webrtcclient.model.message

data class MessageAnswer(
    val type: String,
    val time: Long,
    val data: Data
) {
    data class Data(
        val from: String,
        val to: String,
        val description: Description
    ) {
        data class Description(
            val type: String,
            val sdp: String
        )
    }
}