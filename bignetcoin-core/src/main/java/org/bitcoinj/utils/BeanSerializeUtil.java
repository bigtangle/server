package org.bitcoinj.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class BeanSerializeUtil {

    public static <T> byte[] serializer(T obj) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(obj);
        byte[] buf = byteArrayOutputStream.toByteArray();
        return buf;
    }
    
    public static <T> T deserialize(byte[] buf, Class<T> clazz) throws Exception {
        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(buf));
        @SuppressWarnings("unchecked")
        T obj = (T) objectInputStream.readObject();
        return obj;
    }
}
