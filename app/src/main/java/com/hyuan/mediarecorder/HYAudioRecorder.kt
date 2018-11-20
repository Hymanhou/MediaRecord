package com.hyuan.mediarecorder

import android.media.*
import android.util.Log

import java.io.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val SAMPLE_RATE = 44100
val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

class HYAudioRecorder {

    private val TAG = "HYAudioRecorder"

    private var mAudioRecorder:AudioRecord? = null
    private var mAudioTrack:AudioTrack? = null
    private lateinit var mAudioData: ByteArray
    private lateinit var mFileInputStream: FileInputStream
    private var mIsRecording: Boolean = false
    private val mExecutorService: ExecutorService

    init {
        mExecutorService = Executors.newCachedThreadPool()
    }

    fun startRecord(fileName: String) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        mAudioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG,
            AUDIO_FORMAT, minBufferSize
        )

        val data = ByteArray(minBufferSize)
        val file = File(fileName)
        if (!file.mkdirs()) {
            Log.e(TAG, "directory not created.")
        }
        if (file.exists()) {
            file.delete()
        }

        mAudioRecorder!!.startRecording()
        mIsRecording = true

        mExecutorService.execute {
            var outStream: FileOutputStream? = null
            try {
                outStream = FileOutputStream(file)
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "file not exist!")
            }

            if (outStream != null) {
                while (mIsRecording) {
                    val readBytes = mAudioRecorder!!.read(data, 0, minBufferSize)
                    if (AudioRecord.ERROR_INVALID_OPERATION != readBytes) {
                        try {
                            outStream.write(data)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        Log.e(TAG, "close output file.")
                        outStream.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }
        }
    }

    fun stopRecord() {
        mIsRecording = false
        if (mAudioRecorder != null) {
            mAudioRecorder!!.stop()
            mAudioRecorder!!.release()
            mAudioRecorder = null
        }
    }

    fun playPcm(fileName: String) {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelConfig, AUDIO_FORMAT)
        mAudioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(channelConfig).build(), minBufferSize, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        mAudioTrack!!.play()

        try {
            val inputStream = FileInputStream(File(fileName))
            mExecutorService.execute {
                try {
                    val inBuffer = ByteArray(minBufferSize)
                    while (inputStream.available() > 0) {
                        val readBytes = inputStream.read(inBuffer)
                        if (readBytes == AudioTrack.ERROR_INVALID_OPERATION || readBytes == AudioTrack.ERROR_BAD_VALUE) {
                            continue
                        }
                        if (readBytes > 0) {
                            mAudioTrack!!.write(inBuffer, 0, readBytes)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

    }

    fun stopPlay() {
        if (mAudioTrack != null) {
            mAudioTrack!!.stop()
            mAudioTrack!!.release()
            mAudioTrack = null
        }
    }
}