package com.jungle.localmediaserver;

import java.io.Closeable;
import java.io.IOException;

public class FileUtils {

    public static final int KB = 1024;
    public static final int MB = 1024 * KB;
    public static final int GB = 1024 * MB;


    public static void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
