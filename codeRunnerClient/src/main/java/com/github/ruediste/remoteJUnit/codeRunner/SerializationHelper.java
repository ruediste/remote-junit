package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SerializationHelper {

    private static final class CustomClassloaderObjectInputStream extends
            ObjectInputStream {
        private ClassLoader classLoader;

        private CustomClassloaderObjectInputStream(InputStream in,
                ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected java.lang.Class<?> resolveClass(java.io.ObjectStreamClass desc)
                throws IOException, ClassNotFoundException {
            if (classLoader == null)
                return super.resolveClass(desc);

            try {
                return classLoader.loadClass(desc.getName());
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }

    public static Object toObject(byte[] bytes) {
        return toObject(bytes, null);
    }

    public static Object toObject(byte[] bytes, ClassLoader cl) {
        try (ObjectInputStream in = new CustomClassloaderObjectInputStream(
                new ByteArrayInputStream(bytes), cl)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error during deserialization", e);
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
