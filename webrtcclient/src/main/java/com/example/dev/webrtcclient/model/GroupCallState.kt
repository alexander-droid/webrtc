package com.example.dev.webrtcclient.model

data class GroupCallState(
    var state: State,
    var lastSpeaker: CallUserInfo? = null
) {
    enum class State {
        NONE,
        ME_SPEAKING,
        RECIPIENT_SPEAKING
    }

    companion object {
        fun none(): GroupCallState {
            return GroupCallState(State.NONE)
        }

        fun meSpeaking(userInfo: CallUserInfo): GroupCallState {
            return GroupCallState(State.ME_SPEAKING, userInfo)
        }

        fun recipientSpeaking(userInfo: CallUserInfo): GroupCallState {
            return GroupCallState(State.RECIPIENT_SPEAKING, userInfo)
        }

        fun stoppedSpeaking(userInfo: CallUserInfo): GroupCallState {
            return GroupCallState(State.NONE, userInfo)
        }
    }
}