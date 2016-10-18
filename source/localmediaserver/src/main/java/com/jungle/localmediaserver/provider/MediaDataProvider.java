/*
 * Copyright (C) 2016. All Rights Reserved.
 *
 * @author  Arno Zhang
 * @email   zyfgood12@163.com
 * @date    2016/01/21
 */

package com.jungle.localmediaserver.provider;

import android.util.Log;
import com.jungle.localmediaserver.ServerHttpSession;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public abstract class MediaDataProvider {

    protected String mFilePath;
    protected long mContentLength;


    public MediaDataProvider(String filePath) {
        mFilePath = filePath;
        File file = new File(filePath);
        if (file.exists()) {
            mContentLength = file.length();
        }
    }

    public long getContentLength() {
        return mContentLength;
    }

    public abstract void clean();

    protected abstract int sendVideoDataInternal(
            OutputStream stream, int start, int length) throws IOException;

    public void sendVideoData(OutputStream stream, int start, int length) {
        try {
            int ioCount = sendVideoDataInternal(stream, start, length);

            Log.e(ServerHttpSession.TAG, String.format(
                    "**Video Data Send Successfully! IO-Count = %d.", ioCount));
        } catch (Exception e) {
            Log.e(ServerHttpSession.TAG, "**Write Video Data FAILED!");
            e.printStackTrace();
        }
    }
}
