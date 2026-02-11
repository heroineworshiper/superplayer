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

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {
    static MainActivity instance;
    static TextView volume;
    static TextView current;
    static TextView end;
    static SeekBar progress;
    static Button play;
    static ListView fileList;
    static boolean usingSeekBar = false;
    // just changed directory
    boolean resetListY = true;


    static class SortByName implements Comparator<DirEntry> {
        // Used for sorting in ascending order of
        // roll number
        public int compare(DirEntry a, DirEntry b) {
            int result = a.name.toLowerCase().compareTo(b.name.toLowerCase());
            return result;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainActivity.instance = this;
        Stuff.init(this);
        Log.i("MainActivity", "onCreate currentFile=" + Stuff.currentFile);
        volume = (TextView) findViewById(R.id.volume);
        current = (TextView) findViewById(R.id.currentTime);
        end = (TextView) findViewById(R.id.totalTime);
        progress = (SeekBar) findViewById(R.id.seekBar);
        play = (Button) findViewById(R.id.play);
        fileList = (ListView)findViewById(R.id.fileList);

        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) {
                    int newPosition = value;
                    long seconds = newPosition;
                    long minutes = seconds / 60;
                    seconds = seconds % 60;
                    String formatted = String.format("%d:%02d", minutes, seconds);
                    current.setText(formatted);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                usingSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                usingSeekBar = false;
                int newPosition = seekBar.getProgress();
                if (Stuff.isPlaying) {
                    Player.stop();
                    Stuff.currentTime = newPosition;
                    Player.start();
                } else {
                    Stuff.currentTime = newPosition;
                }
            }
        });


        updateFiles();
        updateButton();
        updateProgress();
        updateVolume();
        Prober.instance.probe();
    }


    public void onClick(View view) {
        boolean wantPlayback;
        switch (view.getId()) {
            case R.id.play:
                if (Stuff.isPlaying)
                    Player.stop();
                else {
                    Player.start();
                    updateButton();
                }
                break;
            case R.id.rewind: {
                wantPlayback = Stuff.isPlaying;
                Player.stop();
                boolean error = false;
                if (Stuff.currentTime < 10) {
                    error = advanceSong(true);
                    if(!error) updateFiles();
                }
                Stuff.currentTime = 0;
                Stuff.save();
                updateProgress();
                if (!error && wantPlayback) Player.start();
                break;
            }
        case R.id.end: {
            wantPlayback = Stuff.isPlaying;
            Player.stop();
            boolean error = advanceSong(false);
            if(!error) updateFiles();
            Stuff.currentTime = 0;
            Stuff.save();
            updateProgress();
            if (!error && wantPlayback) Player.start();
            break;
        }

        case R.id.volumeUp:
                Stuff.volume++;
                Stuff.save();
                updateVolume();
                break;
            case R.id.volumeDown:
                if(Stuff.volume > 0) Stuff.volume--;
                Stuff.save();
                updateVolume();
                break;
        }
    }

    static String upDir(String dir)
    {
        String result = dir;
// strip trailing /
        while(result.length() > 0 && result.charAt(result.length() - 1) == '/')
            result = result.substring(0, result.length() - 1);
        // rewind /
        int i = result.lastIndexOf('/');
        if(i >= 0)
            result = result.substring(0, i);

        if(result.length() == 0) result = "/";
        return result;
    }

    static String extractName(String dir)
    {
        String result = dir;
// strip trailing /
        while(result.length() > 0 && result.charAt(result.length() - 1) == '/')
            result = result.substring(0, result.length() - 1);
// rewind /
        int i = result.lastIndexOf('/');
        if(i >= 0)
            result = result.substring(i + 1);
        return result;
    }

    // return an array with [dir, file]
    static String[] findNext(boolean bwd, String currentDir, String currentFile)
    {
        Log.i("MainActivity", "nextSong bwd=" + bwd + " currentDir=" + currentDir + " currentFile=" + currentFile);
        String[] result = new String[2];
        result[0] = null;
        result[1] = null;

        File dir = new File(currentDir);
        File[] mFileList = dir.listFiles();
        if(mFileList == null) return result;

        DirEntry[] files = new DirEntry[mFileList.length];
        for(int i = 0; i < mFileList.length; i++) {
            //Log.i("FileSelect", "file=" + mFileList[i].getAbsolutePath());
            files[i] = new DirEntry(mFileList[i].getAbsolutePath());
        }

        Arrays.sort(files, new SortByName());

// go to 1st or last file in directory
        if(currentFile.isEmpty())
        {
// go back up if empty
            if(files.length == 0) {
                // up a directory
                String newDir = upDir(currentDir);
                // reject top level & give up
                if (newDir.equals("/"))
                    return result;

                String newFile = extractName(currentDir);
                // continue rewind
                Log.i("MainActivity", "nextSong 1 recursive newDir=" + newDir + " newFile=" + newFile);
                return findNext(bwd, newDir, newFile);
            }

            DirEntry entry;
            if(bwd)
                entry = files[files.length - 1];
            else
                entry = files[0];
            if(entry.isDir)
                return findNext(bwd, currentDir + "/" + entry.name, "");

            result[0] = currentDir;
            result[1] = entry.name;
            return result;
        }

        for(int i = 0; i < files.length; i++)
        {
            if(files[i].name.equals(currentFile))
            {
                if(bwd) {
                    i--;
                    if (i < 0)
                    {
                        // up a directory
                        String newDir = upDir(currentDir);
                        // reject top level & give up
                        if(newDir.equals("/"))
                            return result;

                        String newFile = extractName(currentDir);
                        // continue rewind
                        Log.i("MainActivity", "nextSong 1 recursive newDir=" + newDir + " newFile=" + newFile);
                        return findNext(bwd, newDir, newFile);
                    }
                    else
                    if(files[i].isDir)
                    {
                        // descend into directory
                        String newDir = currentDir + "/" + files[i].name;
                        Log.i("MainActivity", "nextSong 2 recursive newDir=" + newDir);
                        return findNext(bwd, newDir, "");
                    }
                    else
                    {
                        result[0] = currentDir;
                        result[1] = files[i].name;
                        return result;
                    }
                }
                else
                {
                    i++;
                    if(i >= files.length)
                    {
                        // up a directory
                        String newDir = upDir(currentDir);
                        // reject top level & give up
                        if(newDir.equals("/"))
                            return result;

                        String newFile = extractName(currentDir);
                        // continue rewind
                        Log.i("MainActivity", "nextSong newDir=" + newDir + " newFile=" + newFile);
                        return findNext(bwd, newDir, newFile);
                    }
                    else
                    if(files[i].isDir)
                    {
                        // descend into directory
                        String newDir = currentDir + "/" + files[i].name;
                        Log.i("MainActivity", "nextSong 2 recursive newDir=" + newDir);
                        return findNext(bwd, newDir, "");
                    }
                    else
                    {
                        result[0] = currentDir;
                        result[1] = files[i].name;
                        return result;
                    }
                }
            }
        }

        return result;
    }

    // return true if it failed
    static boolean advanceSong(boolean bwd)
    {
        String[] result = findNext(bwd, Stuff.currentDir, Stuff.currentFile);
        if(result[0] != null && result[1] != null) {
            Stuff.currentDir = result[0];
            Stuff.currentFile = result[1];
            Prober.instance.probe();
            return false;
        }
        return true;
    }

    void updateFiles()
    {

        File dir = new File(Stuff.currentDir);
        File[] mFileList = dir.listFiles();

        if(mFileList == null)
        {
            String prevDir = Stuff.currentDir;
            Stuff.currentDir = Stuff.DEFAULT_DIR;
            dir = new File(Stuff.currentDir);
            mFileList = dir.listFiles();
            Log.i("MainActivity", "updateFiles denied access to " + prevDir + " currentDir=" + Stuff.currentDir);
        }

        if(mFileList != null)
        {
            DirEntry[] files = new DirEntry[mFileList.length + 1];
            // the parent dir
            files[0] = new DirEntry("..");
            for(int i = 0; i < mFileList.length; i++) {
                //Log.i("FileSelect", "file=" + mFileList[i].getAbsolutePath());
                files[i + 1] = new DirEntry(mFileList[i].getAbsolutePath());
            }

            Arrays.sort(files, new SortByName());

            ListView listView = (ListView) findViewById(R.id.fileList);

//        Log.v("FileSelect", "onCreate 2 " +
//        		this + " " +
//        		Environment.getRootDirectory() + " " +
//        		Environment.getDataDirectory() + " " +
//        		mPath + " " +
//        		mFileList);
            int listY1 = 0;
            if(!resetListY) listY1 = listView.getFirstVisiblePosition();
            // -1 on startup
            int listH = listView.getLastVisiblePosition() - listView.getFirstVisiblePosition();
            Log.i("MainActivity", "updateFiles 1 " + listView.getFirstVisiblePosition() + " " + listView.getLastVisiblePosition());
            CustomAdapter adapter = new CustomAdapter(this, files);
            resetListY = false;
            listView.setAdapter(adapter);
//            Log.i("MainActivity", "updateFiles 2 " + listView.getFirstVisiblePosition() + " " + listView.getLastVisiblePosition());

            // get the position of currentFile
                for (int i = 0; i < files.length; i++) {
                    if (files[i].name.equals(Stuff.currentFile)) {
                        if (listH > 0) {
                            if (listY1 >= i || listY1 + listH <= i) {
                                Log.i("MainActivity", "updateFiles 3 listY1=" + listY1 + " listH=" + listH + " i=" + i);

                                listY1 = i - listH / 2;
                            }
                        } else {
                            // fudge it to get the selected file to show on startup
                            listY1 = i - 1;
                        }
                        if (listY1 < 0) listY1 = 0;
                        resetListY = true; // get the true Y next time
                        break;
                    }
                }

                listView.setSelection(listY1);

            listView.setOnItemClickListener(new ListView.OnItemClickListener()
            {
                public void onItemClick(AdapterView<?> parent,
                                        View view,
                                        int position,
                                        long id)
                {
                    DirEntry file = files[position];
                    if(file.isDir) {
                        // set the textbox
//                        title.setText(file.name + "/");
//                        Log.i("MainActivity", "updateFiles " + file.name);
                        if (file.name == "..") {
                            // go up a directory
                            //Stuff.currentDir = Stuff.currentDir.substring(0, Stuff.currentDir.length() - 2);
                            Stuff.currentDir = upDir(Stuff.currentDir);
                        } else {
                            // go into the directory
                            Stuff.currentDir += "/" + file.name;
                        }


                        // strip trailing / from directory
                        while(Stuff.currentDir.length() > 1 && Stuff.currentDir.charAt(Stuff.currentDir.length() - 1) == '/')
                            Stuff.currentDir = Stuff.currentDir.substring(0, Stuff.currentDir.length() - 1);
                        Stuff.currentFile = "";
                        // reposition the listview
                        resetListY = true;
                        Stuff.save();

                        updateFiles();
                        Prober.instance.probe();
                    }
                    else if(!Stuff.currentFile.equals(files[position].name))
                    {
                        Player.stop();
                        Stuff.currentFile = files[position].name;
                        Stuff.currentTime = 0;
                        Stuff.length = 0;
                        Prober.instance.probe();
                        Stuff.save();

                        Log.i("MainActivity", "updateFiles " + Stuff.currentFile);
                        adapter.notifyDataSetChanged();
                    }
                }
            });

            // update the directory text
            TextView text = (TextView) findViewById(R.id.currentDir);
            if(text != null) text.setText(Stuff.currentDir + "/");

        }
    }

    void updateButton()
    {
        if(Stuff.isPlaying)
            play.setText(Html.fromHtml("&#9208;").toString());
        else
            play.setText(Html.fromHtml("&#9654;").toString());
    }

    void updateProgress()
    {
        if(!usingSeekBar) {
            long seconds = Stuff.currentTime;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            String formatted = String.format("%d:%02d", minutes, seconds);
            current.setText(formatted);

            progress.setProgress((int)Stuff.currentTime);
            progress.setMax((int)Stuff.length);
        }

        if(Stuff.length > 0) {
            long seconds = Stuff.length;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            String formatted = String.format("%d:%02d", minutes, seconds);
            end.setText(formatted);
        }
        else
        {
            end.setText("UNKNOWN");
        }
    }

    void updateVolume()
    {

        volume.setText(String.valueOf(Stuff.volume));
    }

}
