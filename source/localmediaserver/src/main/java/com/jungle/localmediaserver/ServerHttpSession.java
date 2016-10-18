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
import android.util.SparseArray;
import com.jungle.localmediaserver.provider.MediaDataProvider;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;

/*
 * Local-Http-Server-Session for streaming-video.
 *
 * Request:
 *      GET http://localhost:port/file_path
 *          |
 *          |- Range: bytes=0-xxx
 *
 * Response:
 *
 * 1> Full file response:
 *      HTTP/1.1 200 OK
 *      Content-Length: xxx
 *      Content-Type: video/mp4
 *
 * 2> Partial file response:
 *      HTTP/1.1 206 Partial Content
 *      Content-Length: yyy
 *      Content-Type: video/mp4
 *      Content-Range: mmm-nnn/yyy
 */
public class ServerHttpSession implements Runnable {

    public static final String TAG = "ServerHttpSession";


    private static class StatusCode {
        public static final int OK = 200;
        public static final int PARTIAL_CONTENT = 206;
        public static final int BAD_REQUEST = 400;
        public static final int RANGE_NOT_SATISFIABLE = 416;
        public static final int INTERNAL_SERVER_ERROR = 500;
    }


    private Socket mSocket;
    private MediaDataProvider mVideoProvider;
    private SparseArray<String> mStatusMap = new SparseArray<>();


    public ServerHttpSession(Socket socket, MediaDataProvider videoProvider) {
        mSocket = socket;
        mVideoProvider = videoProvider;
        initStatusMap();
    }

    private void initStatusMap() {
        mStatusMap.put(StatusCode.OK, "OK");
        mStatusMap.put(StatusCode.PARTIAL_CONTENT, "Partial Content");
        mStatusMap.put(StatusCode.BAD_REQUEST, "Bad Request");
        mStatusMap.put(StatusCode.RANGE_NOT_SATISFIABLE, "Range not satisfiable");
        mStatusMap.put(StatusCode.INTERNAL_SERVER_ERROR, "Internal Server Error");
    }

    public void start() {
        Thread thread = new Thread(this, ServerHttpSession.class.getSimpleName());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        InputStream stream = null;
        try {
            stream = mSocket.getInputStream();
            if (stream == null) {
                return;
            }

            Properties header = handleRequest(stream);
            if (header != null) {
                handleResponse(header);
            }
        } catch (Exception e) {
            Log.e(TAG, "Something Wrong.Handle Request & Response FAILED!");
            e.printStackTrace();
        } finally {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            FileUtils.closeStream(stream);
            Log.e(TAG, "Video HttpSession Finished.");
        }
    }

    private Properties handleRequest(InputStream stream) {
        try {
            byte[] buffer = new byte[8 * FileUtils.KB];
            int len = stream.read(buffer);
            if (len < 0) {
                return null;
            }

            ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer, 0, len);
            BufferedReader reader = new BufferedReader(new InputStreamReader(byteStream));
            try {
                return decodeRequestHeader(reader);
            } catch (Exception e) {
                sendError(StatusCode.INTERNAL_SERVER_ERROR, String.format(
                        "Server Internal Error: %s", e.getMessage()));
            }
        } catch (IOException e) {
            Log.e(TAG, "Parse Request Header FAILED!");
            e.printStackTrace();
        }

        return null;
    }

    private void handleResponse(Properties header) {
        final long contentLength = mVideoProvider.getContentLength();
        long start = 0;
        long end = -1;
        int statusCode = StatusCode.OK;
        long sendLength = 0;

        String range = header.getProperty("range");
        if (range == null) {
            end = contentLength - 1;
            sendLength = contentLength;
        } else {
            final String bytesStart = "bytes=";
            if (!range.startsWith(bytesStart)) {
                sendError(StatusCode.RANGE_NOT_SATISFIABLE, String.format(
                        "Range Syntax is Error! [%s]", range));
                return;
            }

            statusCode = StatusCode.PARTIAL_CONTENT;
            range = range.substring(bytesStart.length());
            int separatePos = range.indexOf("-");
            if (separatePos > 0) {
                try {
                    String from = range.substring(0, separatePos);
                    start = Long.parseLong(from);
                    String to = range.substring(separatePos + 1);
                    end = Long.parseLong(to);
                } catch (NumberFormatException e) {
                }
            }

            if (start < 0) {
                start = 0;
            }

            if (end < 0 || end >= contentLength) {
                end = contentLength - 1;
            }

            if (start >= contentLength) {
                sendError(StatusCode.RANGE_NOT_SATISFIABLE, String.format(
                        "Range Error[req = %s]! start = %d, end = %d, sendLength = %d, contentLength = %d.",
                        range, start, end, end - start + 1, contentLength));
                return;
            }

            sendLength = end - start + 1;
            if (sendLength < 0) {
                sendLength = 0;
            }
        }

        Properties rspHeader = new Properties();
        rspHeader.put("Content-Length", String.valueOf(sendLength));

        final String rspRange = String.format("%d-%d/%d", start, end, contentLength);
        if (statusCode != StatusCode.OK) {
            rspHeader.put("Content-Range", String.format("bytes %s", rspRange));
        }

        Log.e(TAG, String.format(
                "Video Request Range[req = %s]: %s, [length = %d].\n",
                String.valueOf(range), rspRange, sendLength));

        sendResponse(statusCode, rspHeader, null, start, sendLength);
    }

    private void sendResponse(
            int statusCode, Properties header, String errorMsg, long start, long length) {

        boolean error = !TextUtils.isEmpty(errorMsg)
                || (statusCode != StatusCode.OK && statusCode != StatusCode.PARTIAL_CONTENT);
        String mimeType = "video/mp4";
        if (error) {
            mimeType = "text/plain";
        }

        OutputStream stream = null;
        try {
            stream = mSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(stream);

            String statusInfo = mStatusMap.get(statusCode);
            if (statusInfo == null) {
                statusInfo = "";
            }

            String result = String.format("HTTP/1.1 %d %s", statusCode, statusInfo);
            writer.print(result + "\r\n");
            Log.i(TAG, result);

            if (header == null) {
                header = new Properties();
            }

            header.put("Accept-Ranges", "bytes");
            header.put("Connection", "keep-alive");
            header.put("Content-Type", mimeType);

            Enumeration<?> e = header.keys();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                String value = header.getProperty(key);
                String item = String.format("%s: %s", key, value);
                writer.print(String.format("%s\r\n", item));

                Log.i(TAG, item);
            }

            writer.print("\r\n");
            writer.flush();

            if (error) {
                writer.print(errorMsg);
                writer.flush();
            } else {
                sendVideoData(stream, start, length);
            }

            stream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Send Response FAILED!");
            e.printStackTrace();
        } finally {
            FileUtils.closeStream(stream);
        }
    }

    private void sendError(int statusCode, String errorMsg) {
        Log.e(TAG, String.format(
                "SendError: statusCode = %d, info = %s, errorMsg = %s",
                statusCode, String.valueOf(mStatusMap.get(statusCode)), errorMsg));

        sendResponse(statusCode, null, errorMsg, 0, 0);
    }

    private Properties decodeRequestHeader(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }

        StringTokenizer tokenizer = new StringTokenizer(line);
        if (!tokenizer.hasMoreTokens()) {
            sendError(StatusCode.BAD_REQUEST, "Syntax Error");
            return null;
        }

        String method = tokenizer.nextToken();
        if (method == null || !method.equalsIgnoreCase("GET")) {
            return null;
        }

        if (!tokenizer.hasMoreElements()) {
            sendError(StatusCode.BAD_REQUEST, "Missing URI");
            return null;
        }

        Properties header = new Properties();
        while (true) {
            line = reader.readLine();
            if (line == null) {
                break;
            }

            int pos = line.indexOf(":");
            if (pos < 0) {
                continue;
            }

            String key = line.substring(0, pos).trim().toLowerCase();
            String value = line.substring(pos + 1).trim().toLowerCase();
            header.put(key, value);

            Log.d(TAG, line);
        }

        return header;
    }

    private void sendVideoData(
            OutputStream stream, long start, long length) {

        Log.e(TAG, String.format("Pre-SendVideoData, start = %d, length = %d.", start, length));
        mVideoProvider.sendVideoData(stream, (int) start, (int) length);
    }
}
