package com.bignetcoin.ui.wallet.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {

    public static byte[] readFile(File file) {
        byte[] buf = null;
        if (file != null) {
            ByteArrayOutputStream byteArrayOutputStream = null;
            BufferedInputStream bufferedInputStream = null;
            byteArrayOutputStream = new ByteArrayOutputStream((int) file.length());
            try {
                bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
                int buffSize = 1024;
                byte[] buffer = new byte[buffSize];
                int len = 0;
                while (-1 != (len = bufferedInputStream.read(buffer, 0, buffSize))) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                buf = byteArrayOutputStream.toByteArray();
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            } finally {
                if (bufferedInputStream != null) {
                    try {
                        bufferedInputStream.close();
                        if (byteArrayOutputStream != null) {
                            byteArrayOutputStream.close();
                        }
                    } catch (IOException e) {
                        GuiUtils.crashAlert(e);
                    }
                }
            }
        }
        return buf;
    }
    
    public static void writeFile(File file, byte[] buf) {
        if (file == null) {
            return;
        }
        if (file.exists()) {
            file.delete();
        }
        BufferedOutputStream bufferedOutputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            bufferedOutputStream.write(buf);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        } finally {
            try {
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        }
    }
}
