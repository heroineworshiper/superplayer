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

import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.util.concurrent.Semaphore;

public class Prober extends Thread {
    static Prober instance;
    Semaphore started = new Semaphore(0);
    String newDir;
    String newFile;
    String prevDir = new String();
    String prevFile = new String();

    Prober(){
        Prober.instance = this;
        start();
    }

    void probe()
    {
        synchronized (this)
        {
            if(Stuff.isPlaying)
            {
                this.newDir = new String(Stuff.playingDir);
                this.newFile = new String(Stuff.playingFile);
            }
            else {
                this.newDir = new String(Stuff.currentDir);
                this.newFile = new String(Stuff.currentFile);
            }
            started.release();
        }
    }

    public void run()
    {
// wait for command
        while(true) {
            try {
                started.acquire();
            } catch (Exception e) {
            }

            boolean gotIt = false;
            synchronized (this)
            {
                if(!prevDir.equals(newDir) ||
                    !prevFile.equals(newFile))
                {
                    prevDir = new String(newDir);
                    prevFile = new String(newFile);
                    gotIt = true;
                }
            }

            if(gotIt)
            {
                long duration = -1;
                MediaMetadataRetriever retriever = null;
                try {
                    retriever = new MediaMetadataRetriever();
                    String path = prevDir + "/" + prevFile;
                    Log.i("Prober", "probing " + path);
                    retriever.setDataSource(path);  // or use Uri: retriever.setDataSource(context, uri);

                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durationStr != null) {
                        duration = Long.parseLong(durationStr); // duration in **milliseconds**
                    }
                    retriever.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Stuff.length = duration / 1000;
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
