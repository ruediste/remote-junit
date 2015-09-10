package com.github.ruediste.remoteJUnit.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

/**
 * Contains a value in serialized form
 */
public class SerializedValue<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte[] data;

    public SerializedValue() {
    }

    public SerializedValue(T value) {
        set(value);
    }

    public void set(T value) {
        ObjectOutputStream out = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out = new ObjectOutputStream(baos);
            out.writeObject(value);
            out.close();
            out = null;
            data = baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error while setting value", e);
        } finally {
            if (out != null)
                try {
                    out.close();
                } catch (IOException e1) {
                    // swallow
                }
        }
    }

    public T get() {
        return get(null);
    }

    @SuppressWarnings("unchecked")
    public T get(ClassLoader cl) {
        ObjectInputStream in = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            in = new ObjectInputStream(bais) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    if (cl == null)
                        return super.resolveClass(desc);
                    try {
                        return cl.loadClass(desc.getName());
                    } catch (ClassNotFoundException e) {
                        return super.resolveClass(desc);
                    }
                }
            };
            return (T) in.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Error while reading value", e);
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException e) {
                    // swallow
                }
        }
    }
}
