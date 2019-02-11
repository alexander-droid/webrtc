package com.example.dev.webrtcclient.model.message

data class MessageOffer(
    val type: String,
    val time: Long,
    val sessionId: String?,
    val data: Data
) {
    data class Data(
        val from: String,
        val to: String,
        val type: String,
        val description: Description
    ) {
        data class Description(
            val type: String,
            val sdp: String
        )
    }
}