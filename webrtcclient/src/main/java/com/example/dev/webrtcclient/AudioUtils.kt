package com.example.dev.webrtcclient

import android.media.MediaMetadataRetriever
import android.util.Log
import org.apache.commons.lang3.time.DurationFormatUtils

object AudioUtils {

    fun getDurationFromUrl(path: String): Long {
        Log.d("AudioUtils", "getDurationFromUrl $path")
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return durationStr.toLong()
        } catch (exc: Exception) {
            Log.e("AudioUtils", "error", exc)
        }

        return 0L
    }

    fun formatAudioDuration(millis: Long): String {
        return DurationFormatUtils.formatDuration(millis, "mm:ss", true)
    }
}