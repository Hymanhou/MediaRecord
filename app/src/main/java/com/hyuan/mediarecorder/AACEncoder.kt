package com.hyuan.mediarecorder

import android.media.*
import java.io.IOException
import java.nio.ByteBuffer

class AACEncoder{
    private val TAG: String = "AACEncoder"
    private val BIT_RATE: Int = 96000
    private val MAX_INPUT_SIZE: Int = 1024 * 1024
    private val ADTS_SIZE: Int = 7

    private lateinit var mMediaCodec: MediaCodec
    private var mMuxer: MediaMuxer

    private var mPresentationTimeUs: Long = 0

    private var mTrackIndex: Int = -1
    private var mIsMuxerStart = false
    private var mIsRecording = false

    constructor(muxer: MediaMuxer) {
        mMuxer = muxer
    }

    fun prepare() {
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


    }

    fun start() {
        mMediaCodec.start()
        mIsRecording = true
    }

    fun drainEncoder(inputByte: ByteArray) {
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
        var bufferInfo = MediaCodec.BufferInfo()
        while (mIsRecording) {
            var bufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
            if (bufferIndex == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                mIsRecording = false
                break
            } else if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue
            } else if (bufferIndex == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                bufferInfo.size = 0
            } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mIsMuxerStart) {
                    throw RuntimeException("format change twice!")
                }
                var mediaFormat = mMediaCodec.getOutputFormat(bufferIndex)
                mTrackIndex = mMuxer.addTrack(mediaFormat)
                mMuxer.start()
                mIsMuxerStart = true
            }

            if (bufferInfo.size > 0) {
                var outputBuffer: ByteBuffer = mMediaCodec.getOutputBuffer(bufferIndex)

                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                mMuxer.writeSampleData(mTrackIndex, outputBuffer, bufferInfo)
            }

            mMediaCodec.releaseOutputBuffer(bufferIndex, false)

            //var outPacketSize: Int = bufferInfo.size + ADTS_SIZE
//            var outData: ByteArray = ByteArray(outPacketSize)
//            addADTStoPacket(outData, outPacketSize)
//
//            outputBuffer.get(outData, 7, outBitsSize)
//            outputBuffer.position(mBufferInfo.offset)
        }
    }

    fun stop() {
        mIsRecording = false
        mMediaCodec.stop()
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

}