package com.example.dev.webrtcclient.call.direct.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.example.dev.webrtcclient.R
import com.example.dev.webrtcclient.call.direct.DirectCallService
import com.example.dev.webrtcclient.call.direct.DirectWebRTCManager


class DirectCallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_call)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            val window = this.window
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }


//        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        audioManager.mode = AudioManager.MODE_IN_CALL
//        audioManager.isSpeakerphoneOn = true

        Log.i(TAG, "onCreate ${intent?.extras?.getString(DirectCallService.EXTRA_CALL_TYPE, null)}")
        if (savedInstanceState == null) {
            intent?.extras?.getString(DirectCallService.EXTRA_CALL_TYPE, null)?.also {
                when(it) {
                    DirectWebRTCManager.CALL_TYPE_VIDEO -> setupVideo()
                    DirectWebRTCManager.CALL_TYPE_VOICE -> setupVoice()
                }
            } ?: run {
                Toast.makeText(this@DirectCallActivity, "EXTRA_CALL_TYPE is missing", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    private fun setupVideo() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, DirectVideoFragment())
                .commitNow()
    }

    private fun setupVoice() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, DirectAudioFragment())
                .commitNow()
    }

    companion object {

        private const val TAG = "DirectCallActivity"

        fun startVideo(context: Context?) {
            context?.startActivity(Intent(context, DirectCallActivity::class.java).apply {
                putExtra(DirectCallService.EXTRA_CALL_TYPE, DirectWebRTCManager.CALL_TYPE_VIDEO)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        fun startVoice(context: Context?) {
            context?.startActivity(Intent(context, DirectCallActivity::class.java).apply {
                putExtra(DirectCallService.EXTRA_CALL_TYPE, DirectWebRTCManager.CALL_TYPE_VOICE)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}