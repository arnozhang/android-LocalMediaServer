package com.jungle.localmediaserver.encryptor;

public interface Encryptor {

    byte[] encrypt(byte[] buffer, byte[] encryptKey);

    byte[] decrypt(byte[] buffer, byte[] encryptKey);

    byte[] encrypt(byte[] buffer, int start, int count, byte[] encryptKey);

    byte[] decrypt(byte[] buffer, int start, int count, byte[] encryptKey);
}
