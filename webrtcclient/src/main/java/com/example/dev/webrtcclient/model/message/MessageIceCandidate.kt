package com.example.dev.webrtcclient.model.message

data class MessageIceCandidate(
    val type: String,
    val time: Long,
    val sessionId: String,
    val data: Data
) {
    data class Data(
        val from: String,
        val to: String,
        val candidate: Candidate?
    ) {
        data class Candidate(
            val candidate: String,
            val sdpMLineIndex: Int,
            val sdpMid: String
        )
    }
}