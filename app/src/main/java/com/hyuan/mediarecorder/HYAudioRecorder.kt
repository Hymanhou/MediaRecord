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

    private lateinit var mAudioRecorder:AudioRecord
    private var mAudioTrack:AudioTrack? = null
    private var mIsRecording: Boolean = false
    private val mExecutorService: ExecutorService
    private var mAACEncoder: AACEncoder

    constructor(muxer: MediaMuxer) {
        mExecutorService = Executors.newCachedThreadPool()
        mAACEncoder = AACEncoder(muxer)
    }

    fun startRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        mAudioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG,
            AUDIO_FORMAT, minBufferSize
        )

        var data = ByteArray(minBufferSize)

        mAudioRecorder.startRecording()
        mAACEncoder.prepare()
        mAACEncoder.start()
        mIsRecording = true

        mExecutorService.execute {
            while (mIsRecording) {
                val readBytes = mAudioRecorder.read(data, 0, minBufferSize)
                if (AudioRecord.ERROR_INVALID_OPERATION != readBytes) {
                    mAACEncoder.drainEncoder(data)
                }
            }
        }
    }

    fun stopRecord() {
        mIsRecording = false
        if (mAudioRecorder != null) {
            mAudioRecorder.stop()
            mAudioRecorder.release()
        }
    }

    fun playPcm(inputStream: InputStream) {
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