/*
 * Copyright (C) 2016. All Rights Reserved.
 *
 * @author  Arno Zhang
 * @email   zyfgood12@163.com
 * @date    2016/01/21
 */

package com.jungle.localmediaserver.provider;

import com.jungle.localmediaserver.encryptor.Encryptor;

import java.io.IOException;
import java.io.OutputStream;

public class EncryptedMediaProvider extends RawFileMediaProvider {

    private byte[] mEncryptKey;
    private Encryptor mEncryptor;


    public EncryptedMediaProvider(String filePath, Encryptor encryptor, byte[] encryptKey) {
        super(filePath);
        mEncryptKey = encryptKey;
        mEncryptor = encryptor;
    }

    @Override
    public void clean() {
        mEncryptKey = null;
        mEncryptor = null;
    }

    @Override
    protected void writeStream(
            OutputStream stream, byte[] buffer, int start, int count) throws IOException {

        byte[] decrypted = mEncryptor.decrypt(buffer, start, count, mEncryptKey);
        stream.write(decrypted, 0, decrypted.length);
    }
}
