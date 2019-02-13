package com.example.dev.webrtcclient.call;

import android.media.AudioFormat;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback;
import org.webrtc.voiceengine.WebRtcAudioRecord;
import org.webrtc.voiceengine.WebRtcAudioRecord.WebRtcAudioRecordSamplesReadyCallback;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Implements the AudioRecordSamplesReadyCallback interface and writes
 * recorded raw audio samples to an output file.
 */
public class RecordedAudioToFileController
        implements SamplesReadyCallback, WebRtcAudioRecordSamplesReadyCallback {
    private static final String TAG = "MyAudioRecord";
    private static final long MAX_FILE_SIZE_IN_BYTES = 58348800L;

    private final Object lock = new Object();
    private final Handler workerHandler;
    @Nullable private OutputStream rawAudioFileOutputStream;
    private boolean isRunning;
    private long fileSizeInBytes;

    private File outputFile;

    public RecordedAudioToFileController(Handler workerHandler) {
        Log.d(TAG, "my_recorder");
        this.workerHandler = workerHandler;
    }

    @Nullable
    public File getOutputFile() {
        return outputFile;
    }

    public void clean() {
        outputFile = null;
    }

    /**
     * Should be called on the same executor thread as the one provided at
     * construction.
     */
    public boolean start() {
        Log.d(TAG, "start");
        if (!isExternalStorageWritable()) {
            Log.e(TAG, "Writing to external media is not possible");
            return false;
        }
        synchronized (lock) {
            isRunning = true;
        }
        return true;
    }

    /**
     * Should be called on the same executor thread as the one provided at
     * construction.
     */
    public void stop() {
        Log.d(TAG, "stop");
        synchronized (lock) {
            isRunning = false;
            if (rawAudioFileOutputStream != null) {
                try {
                    rawAudioFileOutputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close file with saved input audio: " + e);
                }
                rawAudioFileOutputStream = null;
            }
            fileSizeInBytes = 0;
//
//            try {
//                final String fileName = Environment.getExternalStorageDirectory().getPath() + File.separator
//                        + System.currentTimeMillis() + ".wav";
//                rawToWave(outputFile, fileName);
//            } catch (IOException e) {
//                Log.e(TAG, "error", e);
//            }
        }
    }

    // Checks if external storage is available for read and write.
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    // Utilizes audio parameters to create a file name which contains sufficient
    // information so that the file can be played using an external file player.
    // Example: /sdcard/recorded_audio_16bits_48000Hz_mono.pcm.
    private void openRawAudioOutputFile(int sampleRate, int channelCount) {
        final String fileName = Environment.getExternalStorageDirectory().getPath() + File.separator
                + System.currentTimeMillis()
                + "_16bits_" + String.valueOf(sampleRate) + "Hz"
                + ((channelCount == 1) ? "_mono" : "_stereo") + ".pcm";
        outputFile = new File(fileName);
        try {
            rawAudioFileOutputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to open audio output file: " + e.getMessage());
        }
        Log.d(TAG, "Opened file for recording: " + fileName);
    }

    // Called when new audio samples are ready.
    @Override
    public void onWebRtcAudioRecordSamplesReady(WebRtcAudioRecord.AudioSamples samples) {
        onWebRtcAudioRecordSamplesReady(new JavaAudioDeviceModule.AudioSamples(samples.getAudioFormat(),
                samples.getChannelCount(), samples.getSampleRate(), samples.getData()));
    }

    // Called when new audio samples are ready.
    @Override
    public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples samples) {
        Log.v(TAG, "onWebRtcAudioRecordSamplesReady: " + samples.getAudioFormat());
        // The native audio layer on Android should use 16-bit PCM format.
        if (samples.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) {
            Log.e(TAG, "Invalid audio format");
            return;
        }
        synchronized (lock) {
            // Abort early if stop() has been called.
            if (!isRunning) {
                return;
            }
            // Open a new file for the first callback only since it allows us to add audio parameters to
            // the file name.
            if (rawAudioFileOutputStream == null) {
                openRawAudioOutputFile(samples.getSampleRate(), samples.getChannelCount());
                fileSizeInBytes = 0;
            }
        }
        // Append the recorded 16-bit audio samples to the open output file.
        workerHandler.post(() -> {
            if (rawAudioFileOutputStream != null) {
                try {
                    // Set a limit on max file size. 58348800 bytes corresponds to
                    // approximately 10 minutes of recording in mono at 48kHz.
                    if (fileSizeInBytes < MAX_FILE_SIZE_IN_BYTES) {
                        // Writes samples.getData().length bytes to output stream.
                        rawAudioFileOutputStream.write(samples.getData());
                        fileSizeInBytes += samples.getData().length;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write audio to file: " + e.getMessage());
                }
            }
        });
    }














    private File rawToWave(final File rawFile, final String filePath) throws IOException {

        File waveFile = new File(filePath);

        byte[] rawData = new byte[(int) rawFile.length()];
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(rawFile));
            input.read(rawData);
        } finally {
            if (input != null) {
                input.close();
            }
        }

        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new FileOutputStream(waveFile));
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(output, "RIFF"); // chunk id
            writeInt(output, 36 + rawData.length); // chunk size
            writeString(output, "WAVE"); // format
            writeString(output, "fmt "); // subchunk 1 id
            writeInt(output, 16); // subchunk 1 size
            writeShort(output, (short) 1); // audio format (1 = PCM)
            writeShort(output, (short) 1); // number of channels
            writeInt(output, 48000); // sample rate
            writeInt(output, 48000 * 2); // byte rate
            writeShort(output, (short) 2); // block align
            writeShort(output, (short) 16); // bits per sample
            writeString(output, "data"); // subchunk 2 id
            writeInt(output, rawData.length); // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            short[] shorts = new short[rawData.length / 2];
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
            for (short s : shorts) {
                bytes.putShort(s);
            }
            output.write(bytes.array());
        } finally {
            if (output != null) {
                output.close();
            }
        }

        return waveFile;

    }

    private void writeInt(final DataOutputStream output, final int value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    private void writeShort(final DataOutputStream output, final short value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
    }

    private void writeString(final DataOutputStream output, final String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }
}