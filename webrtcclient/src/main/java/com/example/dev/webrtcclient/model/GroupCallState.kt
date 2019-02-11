package com.example.dev.webrtcclient.model

data class GroupCallState(
    var state: State,
    var lastSpeaker: CallUserInfo? = null
) {
    enum class State(value: Int) {
        NONE(0),
        AWAITING_OFFER(1),
        ME_SPEAKING(2),
        RECIPIENT_SPEAKING(2)
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

        fun awaitingOffer(userInfo: CallUserInfo): GroupCallState {
            return GroupCallState(State.AWAITING_OFFER, userInfo)
        }
    }
}