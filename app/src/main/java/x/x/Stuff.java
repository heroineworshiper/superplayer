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

import android.content.Context;
import android.content.SharedPreferences;

public class Stuff {
    static Stuff instance;
    static Context context;
    static final String DEFAULT_DIR = "/sdcard";
    static String currentDir = DEFAULT_DIR;
    static String currentFile = "";
    static String playingDir = "";
    static String playingFile = "";
    static long currentTime = 0; // in seconds
    static int volume = 0;
    static boolean isPlaying = false; // goes false when the player finishes
    static long length = 0; // length of current file in seconds

    static void init(Context context)
    {
        if(instance == null)
        {
            instance = new Stuff();
            Stuff.context = context;
            new Prober();
            load();
        }
    }


    static void load()
    {
        SharedPreferences file = context.getSharedPreferences("settings", 0);
        currentFile = file.getString("currentFile", currentFile);
        currentDir = file.getString("currentDir", currentDir);
        playingFile = file.getString("playingFile", currentFile);
        playingDir = file.getString("playingDir", currentDir);
        volume = file.getInt("volume", volume);
        currentTime = file.getLong("currentTime", currentTime);
        length = file.getLong("length", length);
    }


    static void save()
    {
        synchronized(Stuff.instance) {
            SharedPreferences file2 = context.getSharedPreferences("settings", 0);
            SharedPreferences.Editor file = file2.edit();

            file.putString("playingDir", playingDir);
            file.putString("playingFile", playingFile);
            file.putString("currentDir", currentDir);
            file.putString("currentFile", currentFile);
            file.putInt("volume", volume);
            file.putLong("currentTime", currentTime);
            file.putLong("length", length);
            file.commit();
        }
    }
}
