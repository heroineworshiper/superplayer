/*
 * SuperPlayer
 * Copyright (C) 2026 Adam Williams <broadcast at earthling dot net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package x.x;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

public class Player extends Service {
    static Player instance;
    boolean interrupted;
    boolean error;
    Semaphore done = new Semaphore(0);
    static Semaphore startedLock = new Semaphore(0);
    static boolean started = false;

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i("Player", "onBind");
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onCreate()
    {
        super.onCreate();
        String CHANNEL_ID = "my_channel_01";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT);

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SuperPlayer")
                .setContentText("Playing").build();
        startForeground(1, notification);
    }

    static void start()
    {
        //Log.i("Player", "start instance=" + instance);
        started = true;
        Stuff.isPlaying = true;
        Intent serviceIntent = new Intent(MainActivity.instance, Player.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MainActivity.instance.startForegroundService(serviceIntent);
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId)
    {
        interrupted = false;
        error = false;
        Player.instance = this;

        new Thread(new Runnable(){
            @Override
            public void run() {
// must decode the file to process the audio
                Log.i("Player", "onStartCommand started=" + startedLock);

                startedLock.release();

                while(!interrupted && !error) {
                    playFile();
                    if(!interrupted && !error)
                    {
                        error = MainActivity.advanceSong(false);
                        if(!error)
                            Stuff.currentTime = 0;
                            Stuff.save();
                            MainActivity.instance.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    MainActivity.instance.updateFiles();
                                }
                            });
                    }
                }

                Stuff.isPlaying = false;
                done.release();
                MainActivity.instance.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.instance.updateButton();
                    }
                });
            }

        }).start();

        Log.i("Player", "onStartCommand");

        return START_STICKY;
    }

    void playFile()
    {
        File file = new File(Stuff.currentDir + "/" + Stuff.currentFile);
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            long startTime = Stuff.currentTime;
            boolean seeked = false;
            Timer timer = new Timer();

            Log.i("Player", "run startTime=" + startTime);
            if(startTime > 0) extractor.seekTo(startTime * 1000000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat mf = extractor.getTrackFormat(i);
                String mime = mf.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    MediaCodec decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(mf, null, null, 0);
                    decoder.start();
                    boolean gotInputEOF = false;
                    boolean gotOutputEOF = false;
                    boolean gotPlaybackEOF = false;
                    int samplerate = -1;
                    int channels = -1;
                    int bufsize = 4096; // must be smaller than length
                    AudioTrack track = null;

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream(1024 * 1024); // growable

                    while(!gotOutputEOF && !interrupted && !error) {
// Feed input
                        if (!gotInputEOF) {
                            int inputBufIndex = decoder.dequeueInputBuffer(1000000);
                            if (inputBufIndex >= 0) {
                                ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);
                                int chunkSize = extractor.readSampleData(inputBuf, 0);

                                if (chunkSize < 0) {
                                    // End of stream
                                    decoder.queueInputBuffer(inputBufIndex, 0, 0, 0,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    gotInputEOF = true;
                                } else {
                                    long presentationTimeUs = extractor.getSampleTime();
                                    int flags = extractor.getSampleFlags();

                                    decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                            presentationTimeUs, flags);
                                    extractor.advance();
                                }
                            }
                        }

                        if(!gotOutputEOF)
                        {
                            // Get output
                            int outputBufIndex = decoder.dequeueOutputBuffer(info, 1000000);
                            //Log.i("FileReader", "run: outputBufIndex=" + outputBufIndex + " info.size=" + info.size);

                            if (outputBufIndex >= 0) {
                                ByteBuffer outputBuf = decoder.getOutputBuffer(outputBufIndex);

                                if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                                    // Usually empty for audio decoders \u2192 ignore
                                } else if (info.size > 0) {
                                    // -------------------------------
                                    //  Here is where we get the PCM
                                    // -------------------------------
                                    byte[] chunk = new byte[info.size];
                                    outputBuf.position(info.offset);
                                    outputBuf.get(chunk);               // <--- copy PCM bytes
                                    outputBuf.position(info.offset);    // restore (good practice)
                                    pcmOutput.write(chunk);
                                }

                                decoder.releaseOutputBuffer(outputBufIndex, false);

                                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    gotOutputEOF = true;
                                }
                            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                // New format \u2014 usually happens once
                                MediaFormat newFormat = decoder.getOutputFormat();
                                Log.i("FileReader", "run: Output format changed: " + newFormat);
                                samplerate = newFormat.getInteger("sample-rate");
                                channels = newFormat.getInteger("channel-count");
                                if(startTime > 0 && !seeked)
                                {
// might have to seek after the format changed event
                                    extractor.seekTo(startTime * 1000000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                                    seeked = true;
                                }

                            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                // timeout \u2192 continue
                            }
                        }

//                        Log.i("Player", "playFile flags=" + info.flags + " size=" + pcmOutput.size() + " eof=" + gotOutputEOF);

// empty the pcm buffer
                        while(!interrupted &&
                                !error &&
                                samplerate > 0 &&
                                channels > 0 &&
                                (pcmOutput.size() > bufsize && !gotOutputEOF) ||
                                (pcmOutput.size() > 0 && gotOutputEOF))
                        {
// start the output
                            if(track == null)
                            {
                                int channel_code;
                                if(channels == 1)
                                    channel_code = AudioFormat.CHANNEL_CONFIGURATION_MONO;
                                else
                                    channel_code = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
                                track = new AudioTrack(
                                        AudioManager.STREAM_MUSIC,
                                        samplerate,
                                        channel_code,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        bufsize,
                                        AudioTrack.MODE_STREAM);
                                track.play();
                                MainActivity.instance.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.instance.updateButton();
                                    }
                                });
                            }

// write fragments or flush the buffer
                            if(pcmOutput.size() > bufsize) {
                                byte[] pcm = pcmOutput.toByteArray();
                                applyGain(pcm, 0, bufsize);
                                track.write(pcm, 0, bufsize);
                                pcmOutput = new ByteArrayOutputStream(1024 * 1024);
                                pcmOutput.write(pcm, bufsize, pcm.length - bufsize);
                            }
                            else
                            {
                                Log.i("Player", "playFile flags=" + info.flags + " size=" + pcmOutput.size() + " eof=" + gotOutputEOF);

                                byte[] pcm = pcmOutput.toByteArray();
                                applyGain(pcm, 0, pcmOutput.size());
                                track.write(pcm, 0, pcmOutput.size());
                                Log.i("Player", "playFile flush 1");
                                track.flush();
                                Log.i("Player", "playFile flush 2");
                                pcmOutput = new ByteArrayOutputStream(1024 * 1024);
                            }

                            Stuff.currentTime = (int)(startTime + track.getPlaybackHeadPosition() / samplerate);
                            if(timer.getDiff() > 1000)
                            {
                                timer.reset();
                                Stuff.save();
                                MainActivity.instance.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.instance.updateProgress();
                                    }
                                });

                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            error = true;
        }
        Log.i("Player", "playFile done");
    }

    void applyGain(byte[] pcm, int startByte, int bytes)
    {
        for(int i = 0; i < bytes; i += 2)
        {
            int value = (pcm[startByte + i] & 0xff) +
                    (pcm[startByte + i + 1] << 8); // extend sign
            value *= Stuff.volume + 1;
            if(value > 0x7fff) value = 0x7fff;
            else
            if(value < -0x8000) value = -0x8000;
            pcm[i] = (byte)(value & 0xff);
            pcm[i + 1] = (byte)(value >> 8);
        }
    }


// only call from the UI thread after testing Player.instance
    static void stop()
    {
        if(started) {
            try {
                startedLock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            started = false;
        }

        if(Player.instance != null) {
            Log.i("Player", "stop");
            Player.instance.interrupted = true;
            try {
                Player.instance.done.acquire();
            } catch (Exception e) {
            }
// Only null the instance in the UI thread
            Player.instance = null;
        }
    }
}
