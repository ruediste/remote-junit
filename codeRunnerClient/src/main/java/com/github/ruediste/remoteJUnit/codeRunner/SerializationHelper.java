package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SerializationHelper {

    public static Object toObject(byte[] bytes) {
        return toObject(bytes, null);
    }

    public static Object toObject(byte[] bytes, ClassLoader cl) {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes)) {
            @Override
            protected java.lang.Class<?> resolveClass(
                    java.io.ObjectStreamClass desc) throws IOException,
                    ClassNotFoundException {
                if (cl == null)
                    return super.resolveClass(desc);

                try {
                    return cl.loadClass(desc.getName());
                } catch (ClassNotFoundException e) {
                    return super.resolveClass(desc);
                }
            };
        }) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toByteArray(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out;
        try {
            out = new ObjectOutputStream(baos);
            out.writeObject(obj);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
