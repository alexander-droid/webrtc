package com.example.dev.webrtcclient

const val PUSHER_API_KEY = "11586bf61d7dd9dfdd47"
const val PUSHER_API_KEY_DEV = "b867c4d790c21d83d29e"

const val PUSHER_APP_ID = "533541"
const val PUSHER_APP_ID_DEV = "533539"


const val API_ENDPOINT = "https://com.xsat.io"
const val API_ENDPOINT_DEV = "https://devcom.xsat.io"

object ApiConfig {
    fun apiEndpoint() = API_ENDPOINT_DEV

    fun pusherApiKey() = PUSHER_API_KEY_DEV
}