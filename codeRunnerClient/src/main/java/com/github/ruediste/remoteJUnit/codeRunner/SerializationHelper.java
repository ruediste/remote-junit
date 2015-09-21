package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.function.Function;

public class SerializationHelper {

    /**
     * Helper class loaded with the {@link CodeBootstrapClassLoader}, causing
     * deserialization to use that class loader too.
     */
    static class DeserializationHelper implements Function<byte[], Object> {

        @Override
        public Object apply(byte[] t) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(t))) {
                return ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Object toObject(byte[] bytes) {
        return toObject(bytes, null);
    }

    private static class HelperClassLoader extends ClassLoader {
        HelperClassLoader(ClassLoader parent) {
            super(parent);
            String name = DeserializationHelper.class.getName();
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream(name.replace('.', '/') + ".class")) {
                int read;
                byte[] buffer = new byte[128];
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                while ((read = is.read(buffer)) > 0) {
                    os.write(buffer, 0, read);
                }
                byte[] data = os.toByteArray();
                defineClass(name, data, 0, data.length);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static Object toObject(byte[] bytes, ClassLoader cl) {
        if (cl == null) {
            return new DeserializationHelper().apply(bytes);
        }
        try {
            Constructor<?> cst = new HelperClassLoader(cl)
                    .loadClass(DeserializationHelper.class.getName())
                    .getDeclaredConstructor();
            cst.setAccessible(true);
            @SuppressWarnings("unchecked")
            Function<byte[], Object> helper = (Function<byte[], Object>) cst
                    .newInstance();
            return helper.apply(bytes);
        } catch (Exception e) {
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
