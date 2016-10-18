/*
 * Copyright (C) 2016. All Rights Reserved.
 *
 * @author  Arno Zhang
 * @email   zyfgood12@163.com
 * @date    2016/01/21
 */

package com.jungle.localmediaserver.provider;

import com.jungle.localmediaserver.FileUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class RawFileMediaProvider extends MediaDataProvider {

    public RawFileMediaProvider(String filePath) {
        super(filePath);
    }

    @Override
    public void clean() {
    }

    @Override
    protected int sendVideoDataInternal(
            OutputStream stream, int start, int length) throws IOException {

        RandomAccessFile file = null;

        try {
            file = new RandomAccessFile(mFilePath, "r");
            file.seek(start);

            final int buffLength = 8 * FileUtils.KB;
            final byte[] buffer = new byte[buffLength];

            int ioCount = 0;
            int count = 0;
            while (length > 0) {
                count = file.read(buffer, 0, Math.min(length, buffLength));
                if (count < 0) {
                    return ioCount;
                }

                ++ioCount;
                writeStream(stream, buffer, 0, count);
                length -= count;
            }

            return ioCount;
        } finally {
            FileUtils.closeStream(file);
        }
    }

    protected void writeStream(
            OutputStream stream, byte[] buffer, int start, int count) throws IOException {

        stream.write(buffer, start, count);
        stream.flush();
    }
}
