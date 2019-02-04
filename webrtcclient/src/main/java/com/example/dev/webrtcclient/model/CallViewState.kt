package com.example.dev.webrtcclient.model

enum class CallViewState(value: Int) {
    NONE(0),
    CALLING_IN(1),
    CALLING_OUT(2),
    CALL_RUNNING(3)
}