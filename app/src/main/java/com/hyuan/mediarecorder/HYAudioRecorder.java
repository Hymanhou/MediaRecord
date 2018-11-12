package com.hyuan.mediarecorder;

import android.media.*;
import android.util.Log;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HYAudioRecorder {
    private static final String TAG = "HYAudioRecorder";

    private AudioRecord mAudioRecorder;
    private AudioTrack mAudioTrack;
    private byte[] mAudioData;
    private FileInputStream mFileInputStream;
    private boolean mIsRecording;
    private ExecutorService mExecutorService;

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public HYAudioRecorder() {
        mExecutorService = Executors.newCachedThreadPool();
    }

    public void startRecord(String fileName) {
        final int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, minBufferSize);
        final byte data[] = new byte[minBufferSize];
        final File file = new File(fileName);
        if (!file.mkdirs()) {
            Log.e(TAG, "directory not created.");
        }
        if (file.exists()) {
            file.delete();
        }

        mAudioRecorder.startRecording();
        mIsRecording = true;

        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                FileOutputStream outStream = null;
                try {
                    outStream = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "file not exist!");
                }

                if (outStream != null) {
                    while (mIsRecording) {
                        int readBytes = mAudioRecorder.read(data, 0, minBufferSize);
                        if (AudioRecord.ERROR_INVALID_OPERATION != readBytes) {
                            try {
                                outStream.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            Log.e(TAG, "close output file.");
                            outStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    public void stopRecord() {
        mIsRecording = false;
        if (mAudioRecorder != null) {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }
    }

    public void playPcm(String fileName) {
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        final int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelConfig, AUDIO_FORMAT);
        mAudioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(channelConfig).build(), minBufferSize, AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE);
        mAudioTrack.play();

        try {
            final FileInputStream inputStream = new FileInputStream(new File(fileName));
            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] inBuffer = new byte[minBufferSize];
                        while (inputStream.available() > 0) {
                            int readBytes = inputStream.read(inBuffer);
                            if (readBytes == AudioTrack.ERROR_INVALID_OPERATION ||
                                    readBytes == AudioTrack.ERROR_BAD_VALUE) {
                                continue;
                            }
                            if (readBytes > 0) {
                                mAudioTrack.write(inBuffer, 0, readBytes);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (FileNotFoundException e)  {
            e.printStackTrace();
        }
    }

    public void stopPlay() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }
}