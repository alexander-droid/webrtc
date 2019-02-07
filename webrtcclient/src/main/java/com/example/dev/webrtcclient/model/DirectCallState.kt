package com.example.dev.webrtcclient.model

enum class DirectCallState(value: Int) {
    NONE(0),

    INITIALIZING_CALLING_IN(1),
    CALLING_IN(2),
    AWAITING_OFFER(3),
    SETTING_OFFER(4),
    CREATING_ANSWER(5),

    INITIALIZING_CALLING_OUT(1),
    CALLING_OUT(2),
    CREATING_OFFER(3),
    AWAITING_ANSWER(4),
    SETTING_ANSWER(5),

    CALL_RUNNING(6)
}