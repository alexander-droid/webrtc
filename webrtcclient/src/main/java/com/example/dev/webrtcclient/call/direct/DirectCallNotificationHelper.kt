package com.example.dev.webrtcclient.call.direct

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.example.dev.webrtcclient.R
import com.example.dev.webrtcclient.call.direct.ui.DirectCallActivity

class DirectCallNotificationHelper(private val context: Context) {

    private var notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createCallNotificationChannel()
        createIncomingCallNotificationChannel()
    }


    fun getInComingCallNotification(callType: String, name: String): Notification {

        val answerIntent = DirectCallService.getAnswerIntent(context)
        val answerPendingIntent: PendingIntent = PendingIntent.getService(context, 0, answerIntent, 0)

        val declineIntent = DirectCallService.getDeclineIntent(context)
        val declinePendingIntent: PendingIntent = PendingIntent.getService(context, 0, declineIntent, 0)


        val contentText: String
        val answerIcon: Int
        when (callType) {
            DirectWebRTCManager.CALL_TYPE_VIDEO -> {
                contentText = "Incoming video call"
                answerIcon = R.drawable.ic_video_call_green_24dp
            }
            DirectWebRTCManager.CALL_TYPE_VOICE -> {
                contentText = "Incoming voice call"
                answerIcon = R.drawable.ic_call_green_24dp
            }
            else -> {
                contentText = "Unknown"
                answerIcon = R.drawable.ic_call_green_24dp
            }
        }

        val answerString = SpannableString("Answer")
        answerString.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.green)), 0, answerString.length, 0)

        val declineString = SpannableString("Decline")
        declineString.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.red)), 0, declineString.length, 0)

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        return NotificationCompat.Builder(context, DirectCallService.CHANNEL_ID_INCOMING_CALL)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSmallIcon(R.drawable.ic_notificatio)
                .setContentTitle(name)
//                .setSound(Settings.System.DEFAULT_RINGTONE_URI, AudioManager.STREAM_NOTIFICATION)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(getPendingContentIntent(callType))
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(false)
//                .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000))
                .setColor(Color.parseColor("#ff2ca5e0"))
                .addAction(answerIcon, answerString, answerPendingIntent)
                .addAction(R.drawable.ic_call_end_red_24dp, declineString, declinePendingIntent)
                .setFullScreenIntent(PendingIntent.getActivity(context, 0, getDirectCallContentIntent(callType), 0), true)
                .build()
    }

    fun getOutComingCallNotification(callType: String, name: String): Notification {
        val cancelIntent = DirectCallService.getDeclineIntent(context)
        val cancelPendingIntent: PendingIntent = PendingIntent.getService(context, 0, cancelIntent, 0)

        val contentText = when (callType) {
            DirectWebRTCManager.CALL_TYPE_VIDEO -> "Current video call"
            DirectWebRTCManager.CALL_TYPE_VOICE -> "Current voice call"
            else -> "Unknown"
        }

        return NotificationCompat.Builder(context, DirectCallService.CHANNEL_ID_CALL)
            .setSmallIcon(R.drawable.ic_notificatio)
            .setContentTitle(name)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingContentIntent(callType))
            .setAutoCancel(false)
            .setSound(null)
            .setVibrate(longArrayOf())
            .addAction(R.drawable.ic_call_end_red_24dp, "Cancel", cancelPendingIntent)
            .build()
    }

    fun getRunningCallNotification(callType: String, name: String): Notification {
        val cancelIntent = DirectCallService.getDeclineIntent(context)
        val cancelPendingIntent: PendingIntent = PendingIntent.getService(context, 0, cancelIntent, 0)

        val contentText = when (callType) {
            DirectWebRTCManager.CALL_TYPE_VIDEO -> "Video call"
            DirectWebRTCManager.CALL_TYPE_VOICE -> "Voice call"
            else -> "Unknown"
        }

        return NotificationCompat.Builder(context, DirectCallService.CHANNEL_ID_CALL)
            .setSmallIcon(R.drawable.ic_notificatio)
            .setContentTitle(name)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingContentIntent(callType))
            .setAutoCancel(false)
            .setSound(null)
            .setVibrate(longArrayOf())
            .addAction(R.drawable.ic_call_end_red_24dp, "Hangup", cancelPendingIntent)
            .build()
    }


    private fun getPendingContentIntent(callType: String): PendingIntent {
        val contentIntent = getDirectCallContentIntent(callType)
        return PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getDirectCallContentIntent(callType: String): Intent {
        return Intent(context, DirectCallActivity::class.java).apply {
            putExtra(DirectCallService.EXTRA_CALL_TYPE, callType)

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }










    private fun createIncomingCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Create the NotificationChannel
            val name = context.getString(R.string.channel_name_incoming_call)
            val descriptionText = context.getString(R.string.channel_description_incoming_call)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val mChannel = NotificationChannel(DirectCallService.CHANNEL_ID_INCOMING_CALL, name, importance)
            mChannel.enableVibration(true)
            mChannel.vibrationPattern = longArrayOf(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000)
            mChannel.enableLights(true)
            mChannel.lightColor = ContextCompat.getColor(context, R.color.red)
            mChannel.description = descriptionText
            mChannel.setSound(Settings.System.DEFAULT_RINGTONE_URI, AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build())

            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Create the NotificationChannel
            val name = context.getString(R.string.channel_name_call)
            val descriptionText = context.getString(R.string.channel_description_call)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(DirectCallService.CHANNEL_ID_CALL, name, importance)
            mChannel.description = descriptionText
            mChannel.enableLights(false)
            mChannel.enableVibration(false)
            mChannel.vibrationPattern = longArrayOf()
            mChannel.setSound(null, null)

            notificationManager.createNotificationChannel(mChannel)
        }
    }
}