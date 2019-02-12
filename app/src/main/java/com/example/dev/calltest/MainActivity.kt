package com.example.dev.calltest

import android.Manifest
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import com.crashlytics.android.Crashlytics
import com.example.dev.webrtcclient.EVENT_CALL
import com.example.dev.webrtcclient.call.direct.DirectCallService
import com.example.dev.webrtcclient.call.direct.DirectWebRTCManager
import com.example.dev.webrtcclient.call.ptt.PTTCallService
import com.example.dev.webrtcclient.log.LogAdapter
import com.example.dev.webrtcclient.model.request.RequestCall
import com.google.gson.Gson
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.PrivateChannel
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpAuthorizer
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var pusher: Pusher
    private var myChannel: PrivateChannel? = null

    private lateinit var logAdapter: LogAdapter

    private val myEventListener = object : PrivateChannelEventListener {

        override fun onEvent(channelName: String, eventName: String, data: String) {
            when(eventName) {
                EVENT_CALL -> {
                    val request = Gson().fromJson(data, RequestCall.Data::class.java)
                    if (request.type == DirectWebRTCManager.CALL_TYPE_VOICE) {
                        DirectCallService.acceptVoice(this@MainActivity, myNameEditText.text.toString(), request.caller, request.channel)
                    } else if (request.type == DirectWebRTCManager.CALL_TYPE_VIDEO) {
                        DirectCallService.acceptVideo(this@MainActivity, myNameEditText.text.toString(), request.caller, request.channel)
                    }
                }
            }
        }

        override fun onAuthenticationFailure(message: String?, e: Exception?) {

        }

        override fun onSubscriptionSucceeded(channelName: String) {

        }
    }

    override fun onResume() {
        super.onResume()
//        bindService(Intent(this, DirectCallService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
//        unbindService(serviceConnection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter
        initPusher()

        callVideoBtn.setOnClickListener {
            DirectCallService.startVideo(this, myNameEditText.text.toString(), opponentEditText.text.toString())
        }

        callVoiceBtn.setOnClickListener {
            DirectCallService.startVoice(this, myNameEditText.text.toString(), opponentEditText.text.toString())
        }

        callGroupBtn.setOnClickListener {
            PTTCallService.startGroup(this, myNameEditText.text.toString(), opponentEditText.text.toString())
        }

        myChannelBtn.setOnClickListener {

            RxPermissions(this).request(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
                .subscribe({ granted ->
                    if (granted) {
                        createChannel()
                    }
                }, {
                    Log.e("MainActivity", "Error", it)
                })
        }
    }

    private fun createChannel() {
        val privateName = "private-${myNameEditText.text.toString()}"

        if (myChannel?.isSubscribed == true) {
            myChannel?.unbind(EVENT_CALL, myEventListener)
            pusher.unsubscribe(myChannel?.name)
        }

        myChannel = pusher.subscribePrivate(privateName, object : PrivateChannelEventListener {

            override fun onEvent(channelName: String?, eventName: String?, data: String?) {

            }

            override fun onAuthenticationFailure(message: String?, e: java.lang.Exception?) {
                runOnUiThread {
                    myChannelTextView.text = "Subscription Error: ${message}"
                    myChannelTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorRed))
                }
            }

            override fun onSubscriptionSucceeded(channelName: String?) {
                runOnUiThread {
                    call_container.visibility = View.VISIBLE
                    myChannelTextView.text = "Subscribed on \"$channelName\""
                    myChannelTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorBlue))
                }
            }

        })
        myChannel?.bind(EVENT_CALL, myEventListener)
    }

    private fun initPusher() {
        pusher = Pusher("b867c4d790c21d83d29e", PusherOptions().apply {
            setCluster("eu")
            authorizer = HttpAuthorizer("https://devcom.xsat.io/api/pusher/auth")
        })

        pusher.connect(object : ConnectionEventListener {

            override fun onConnectionStateChange(change: ConnectionStateChange?) {
                runOnUiThread {
                    when(change?.currentState) {
                        ConnectionState.CONNECTED -> {
                            content.visibility = View.VISIBLE
                        }
                        else -> {
                            content.visibility = View.GONE
                        }
                    }
                    change?.currentState?.also { state ->
                        pusherStateTextView.text = state.name
                    }
                }
            }

            override fun onError(message: String?, code: String?, e: Exception?) {
                Log.e("MainActivity", "Pusher connection error $message", e)
                val errorMessage = message ?: "Unknown Pusher connection error"
                runOnUiThread {
                    pusherStateTextView.text = errorMessage
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (myChannel?.isSubscribed == true) {
            myChannel?.unbind(EVENT_CALL, myEventListener)
            pusher.unsubscribe(myChannel?.name)
        }
    }
}
