/*
 * Copyright (C) 2016. All Rights Reserved.
 *
 * @author  Arno Zhang
 * @email   zyfgood12@163.com
 * @date    2016/01/21
 */

package com.jungle.localmediaserver;

import android.text.TextUtils;
import android.util.Log;
import com.jungle.localmediaserver.provider.MediaDataProvider;
import com.jungle.localmediaserver.provider.RawFileMediaProvider;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;

/*
 * Local-Video-Server for streaming-video.
 *
 * Receive & Handle Http Requests:
 *      |
 *      |- request_1: GET http://localhost:port/file_path [Range: bytes=0-xxx]
 *      |- request_2: GET http://localhost:port/file_path [Range: bytes=xxx-yyy]
 *      |- request_3: ...
 *
 */
public class LocalMediaServer {

    private static final String TAG = "LocalMediaServer";

    private Thread mWorkThread;
    private ServerSocket mServerSocket;
    private MediaDataProvider mVideoProvider;
    private boolean mIsWorking = false;


    public LocalMediaServer() {
    }

    public String prepare(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() <= 0) {
            return null;
        }

        try {
            mServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mServerSocket == null) {
            return null;
        }

        String url = getFileUrl(filePath);
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        mVideoProvider = createMediaProvider(filePath);
        return url;
    }

    protected MediaDataProvider createMediaProvider(String filePath) {
        return new RawFileMediaProvider(filePath);
    }

    public boolean start() {
        if (mVideoProvider == null) {
            return false;
        }

        mWorkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mIsWorking) {
                    try {
                        Socket socket = mServerSocket.accept();
                        if (!mIsWorking || socket == null) {
                            break;
                        }

                        Log.e(TAG, "Recv New Video Request!");
                        ServerHttpSession session = new ServerHttpSession(
                                socket, mVideoProvider);
                        session.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mIsWorking = true;
        mWorkThread.setName(LocalMediaServer.class.getSimpleName());
        mWorkThread.setDaemon(true);
        mWorkThread.start();

        return true;
    }

    private String getFileUrl(String filePath) {
        if (mServerSocket == null || TextUtils.isEmpty(filePath)) {
            return null;
        }

        int port = mServerSocket.getLocalPort();
        try {
            return "http://localhost:" + port
                    + "/" + URLEncoder.encode(filePath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void stop() {
        mIsWorking = false;

        if (mVideoProvider != null) {
            mVideoProvider.clean();
        }

        try {
            if (mServerSocket != null) {
                mServerSocket.close();
            }

            if (mWorkThread != null) {
                mWorkThread.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
