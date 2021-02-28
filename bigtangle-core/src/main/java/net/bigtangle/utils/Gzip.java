package net.bigtangle.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Gzip {

    public static byte[] decompress(byte[] contentBytes) {
        return decompressOut(contentBytes).toByteArray();
    }

    public static String decompressString(byte[] contentBytes) throws UnsupportedEncodingException {
        return decompressOutString(contentBytes);
    }

    public static String decompressOutString(byte[] contentBytes) {
        if (contentBytes.length == 0)
            return null;
        Writer out = new StringWriter();
        GZIPInputStream gzis = null;
        try {

            gzis = new GZIPInputStream(new ByteArrayInputStream(contentBytes));
            Reader reader = new InputStreamReader(gzis, "UTF-8");
            char[] buffer = new char[10240];
            for (int length = 0; (length = reader.read(buffer)) > 0;) {
                out.write(buffer, 0, length);
            }

            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
            try {
                gzis.close();
            } catch (Exception e) {
            }

        }
    }

    public static ByteArrayOutputStream decompressOut(byte[] contentBytes) {
        if (contentBytes.length == 0)
            return null;
        ByteArrayOutputStream out = null;
        GZIPInputStream gzis = null;
        try {
            out = new ByteArrayOutputStream();
            gzis = new GZIPInputStream(new ByteArrayInputStream(contentBytes));
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzis.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
            try {
                gzis.close();
            } catch (Exception e) {
            }

        }
    }

    public static byte[] compress(byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        try (GZIPOutputStream out = new GZIPOutputStream(bos)) {
            out.write(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bos.toByteArray();

    }
}
