package com.hyuan.mediarecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.lang.RuntimeException
import java.util.concurrent.Executors

class HYMediaRecorder {

    private val TAG = "HYMediaRecorder"

    private val MIME_TYPE = "video/avc"
    private val FRAME_RATE = 30
    private val I_FRAME_INTERVAL = 10

    private var VIDEO_WIDTH = 320
    private var VIDEO_HEIGHT = 240
    private var BIT_RATE = 2000000

    private var mVideoCodec: MediaCodec? = null
    private var mAudioCodec: MediaCodec? = null
    private var mSurface: Surface? = null
    private var mMediaMuxer: MediaMuxer? = null
    private var mIsRecording = false
    private var mFilePath = "";

    private var mExecutorService = Executors.newCachedThreadPool();

    fun setSize(width: Int, height: Int) {
        VIDEO_WIDTH = width
        VIDEO_HEIGHT = height
    }

    fun setFilePath(path: String) {
        mFilePath = path
    }

    fun prepare() {
        mMediaMuxer = MediaMuxer(mFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mVideoCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        var mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        mediaFormat.apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        mVideoCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mSurface = mVideoCodec!!.createInputSurface()
    }

    fun getSurface(): Surface?{
        return mSurface
    }

    fun start(){

        mVideoCodec!!.start()
        mIsRecording = true

        mExecutorService.submit(){
            var trackIndex = -1
            var bufferInfo = MediaCodec.BufferInfo()
            var muxerStart = false;
            while (mIsRecording) {
                var bufferIndex = mVideoCodec!!.dequeueOutputBuffer(bufferInfo, 10000)
                if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStart) {
                        throw RuntimeException("format changed twice")
                    }

                    var outputMediaFormat = mVideoCodec!!.getOutputFormat()
                    trackIndex = mMediaMuxer!!.addTrack(outputMediaFormat)
                    mMediaMuxer!!.start()
                    muxerStart = true

                    Log.i(TAG,"muxer start")
                } else if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.i(TAG, "no output buffer")
                    continue
                } else if (bufferIndex < 0) {
                    continue
                } else {
                    if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                        bufferIndex = 0
                    }

                    if (bufferIndex > 0) {
                        if (!muxerStart) {
                            throw RuntimeException("muxer hasn't started.")
                        }
                        var byteBufferData = mVideoCodec!!.getOutputBuffer(bufferIndex)
                        if (byteBufferData == null) {
                            throw RuntimeException("buffer data at:$bufferIndex is null")
                        }

                        byteBufferData.position(bufferInfo.offset)
                        byteBufferData.limit(bufferInfo.offset + bufferInfo.size)
                        mMediaMuxer!!.writeSampleData(trackIndex, byteBufferData, bufferInfo)
                    }

                    mVideoCodec!!.releaseOutputBuffer(bufferIndex, false);

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        mIsRecording = false
                        break
                    }

                }
            }
        }

    }

    fun stop() {
        if (mVideoCodec != null && mIsRecording) {
            mIsRecording = false
            mVideoCodec!!.stop()
            mVideoCodec!!.release()
            mVideoCodec = null
        }

        if (mMediaMuxer != null) {
            mMediaMuxer!!.stop()
            mMediaMuxer!!.release()
            mMediaMuxer = null
        }
    }

}