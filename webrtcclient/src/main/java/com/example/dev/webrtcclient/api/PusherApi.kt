package com.example.dev.webrtcclient.api

import com.example.dev.webrtcclient.model.request.RequestCall
import com.example.dev.webrtcclient.model.request.RequestDecline
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface PusherApi {

    @POST("api/push/pusher")
    fun requestCall(@Body body: RequestCall): Call<Unit>

    @POST("api/push/pusher")
    fun requestDecline(@Body body: RequestDecline): Call<Unit>
}