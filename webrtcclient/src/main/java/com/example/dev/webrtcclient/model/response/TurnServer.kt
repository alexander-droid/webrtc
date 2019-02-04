package com.example.dev.webrtcclient.model.response

import com.google.gson.annotations.SerializedName

data class TurnServer(

    @SerializedName("s")
    var s: String? = null,

    @SerializedName("p")
    var p: String? = null,

    @SerializedName("e")
    var e: Any? = null,

    @SerializedName("v")
    var iceServerList: IceServerList? = null
) {
    data class IceServerList(

        @SerializedName("iceServers")
        var iceServers: List<IceServer>? = null

    )

    data class IceServer(

        @SerializedName("url")
        var url: String? = null,

        @SerializedName("username")
        var username: String? = null,

        @SerializedName("credential")
        var credential: String? = null
    )
}