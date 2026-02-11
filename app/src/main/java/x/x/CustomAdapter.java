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
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

class CustomAdapter extends ArrayAdapter<String> {

    Context context;
    DirEntry[] files;

    public CustomAdapter(Context context, DirEntry[] files) {
        super(context,
                android.R.layout.simple_list_item_1,
                android.R.id.text1);
        this.context = context;
        this.files = files;
    }

    public void setHighlightPosition(int position) {
        notifyDataSetChanged();   // very important!
    }

    @Override
    public int getCount() {
        return files.length;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;

        if (row == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
        }

        TextView textView = row.findViewById(android.R.id.text1);
        //Log.i("CustomAdapter", "textView=" + textView + " position=" + position);
        if (textView != null) {
            DirEntry file = files[position];
            if (file.isDir) {
                textView.setText(file.name + "/");
                textView.setTextColor(Color.BLUE);
                textView.setBackgroundColor(Color.WHITE);
            } else {
//                    Log.i("CustomAdapter", "getView name=" + file.name + " currentFile=" + Stuff.currentFile);

                textView.setText(file.name);
                textView.setTextColor(Color.BLACK);
                if (Stuff.currentFile.compareTo(file.name) == 0) {
                    textView.setBackgroundColor(Color.GREEN);
//                        Log.i("CustomAdapter", "getView name=" + file.name + " currentFile=" + Stuff.currentFile);
                } else
                    textView.setBackgroundColor(Color.WHITE);
            }
//                textView.setEllipsize(TextUtils.TruncateAt.END);
//                textView.setMaxLines(1);
//                textView.setMarqueeRepeatLimit(-1);
            textView.setIncludeFontPadding(false);
            textView.setPaddingRelative(0, 0, 0, 0);
            textView.setPadding(0, 0, 0, 0);
            textView.setLineSpacing(0, 0);
        }

//        Log.i("CustomAdapter", "getView 1 " + MainActivity.fileList.getFirstVisiblePosition() +
//                " " + MainActivity.fileList.getLastVisiblePosition());

        return row;
    }
}
