package com.hyuan.mediarecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class AACEncoder:Runnable{
    private val TAG: String = "AACEncoder"
    private val BIT_RATE: Int = 96000
    private val MAX_INPUT_SIZE: Int = 1024 * 1024
    private val ADTS_SIZE: Int = 7

    private lateinit var mMediaCodec: MediaCodec
    private var mMediaType: String = "OMX.goole.aac.encoder"

    private lateinit var mOutputBuffers: ByteBuffer
    private lateinit var mBufferInfo: MediaCodec.BufferInfo
    private var mPresentationTimeUs: Long = 0

    private var outputStream: ByteArrayOutputStream = ByteArrayOutputStream()

    init {
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        } catch (e: IOException) {
            e.printStackTrace()
        }

        var mediaFormat: MediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE,
            CHANNEL_CONFIG)

        mediaFormat.apply {
            setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }

        mMediaCodec.configure(mediaFormat, null, null,MediaCodec.CONFIGURE_FLAG_ENCODE)
        mMediaCodec.start()
    }

    fun drainEncoder(inputByte: ByteArray):ByteArray {
        var inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1)
        if (inputBufferIndex >= 0) {
            var inputBuffer: ByteBuffer = mMediaCodec.getInputBuffer(inputBufferIndex)
            inputBuffer.clear()
            inputBuffer.put(inputByte)
            inputBuffer.limit(inputByte.size)

            var pts: Long = computePts(mPresentationTimeUs)
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, inputByte.size, pts, 0)
            mPresentationTimeUs++
        }

        var outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0)

        while (outputBufferIndex >= 0) {
            var outBitsSize: Int = mBufferInfo.size
            var outPacketSize: Int = outBitsSize + ADTS_SIZE
            var outputBuffer: ByteBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex)

            outputBuffer.position(mBufferInfo.offset)
            outputBuffer.limit(mBufferInfo.offset + outBitsSize)

            var outData: ByteArray = ByteArray(outPacketSize)
            addADTStoPacket(outData, outPacketSize)

            outputBuffer.get(outData, 7, outBitsSize)
            outputBuffer.position(mBufferInfo.offset)

            outputStream.write(outData)

            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false)
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0)
        }

        var out: ByteArray = outputStream.toByteArray()

        outputStream.flush()
        outputStream.reset()

        return out
    }

    private fun computePts(frameIndex: Long): Long {
        return frameIndex * BIT_RATE * 1024 / SAMPLE_RATE
    }

    private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
        var profile = 2 //AAC LC
        var freqIdx = 4 //44.1KHz
        var channelCfg = CHANNEL_CONFIG //CPE

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (channelCfg shr 2)).toByte()
        packet[3] = (((channelCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    override fun run() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}