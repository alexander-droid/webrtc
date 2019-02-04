package com.example.dev.webrtcclient.api

import com.example.dev.webrtcclient.model.response.TurnServer
import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.PUT

interface XirsysApi {

    @PUT("/_turn/net.cilicon.xplore.dev")
    fun getIceCandidates(@Header("Authorization") auth: String): Call<TurnServer>

}