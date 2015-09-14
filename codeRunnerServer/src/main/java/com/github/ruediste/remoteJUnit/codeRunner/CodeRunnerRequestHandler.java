package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.CustomRequest;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.RemoteCode;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.Request;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.RunCodeRequest;

public class CodeRunnerRequestHandler {

    private final static Logger log = LoggerFactory
            .getLogger(CodeRunnerRequestHandler.class);

    private AtomicLong nextCodeId = new AtomicLong(0);
    private ConcurrentMap<Long, RemoteCode> remoteCodes = new ConcurrentHashMap<>();

    private Executor executor;

    public CodeRunnerRequestHandler() {
        this(Executors.newCachedThreadPool()::execute);
    }

    /**
     * @param executor
     *            Used to execute the supplied {@link RemoteCode}s. The executor
     *            MUST return immediately, starting the supplied runnable in a
     *            separate thread.
     */
    public CodeRunnerRequestHandler(Executor executor) {
        this.executor = executor;
    }

    public CodeRunnerCommon.Response handle(InputStream in) throws IOException {
        ObjectInputStream oin = new ObjectInputStream(in);
        CodeRunnerCommon.Request req = null;
        try {
            req = (Request) oin.readObject();
        } catch (ClassNotFoundException e) {
            log.error("Error while parsing request", e);
            return new CodeRunnerCommon.FailureResponse(e);
        }

        return handleRequest(req);

    }

    public static byte[] toByteArray(Object resp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
            out.writeObject(resp);
        }
        return baos.toByteArray();
    }

    private static class CodeBootstrapClassLoader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }
        private Map<String, byte[]> classData;

        private CodeBootstrapClassLoader(Map<String, byte[]> classData) {
            this.classData = classData;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (name.endsWith(".class")) {
                byte[] data = classData.get(name.substring(0,
                        name.length() - ".class".length()).replace('/', '.'));
                if (data != null)
                    return new ByteArrayInputStream(data);
            }
            return super.getResourceAsStream(name);
        }

        @Override
        public java.lang.Class<?> findClass(String name)
                throws ClassNotFoundException {

            byte[] data = classData.get(name);
            if (data != null) {
                return defineClass(name, data, 0, data.length);
            } else {
                if (DeserializationHelper.class.getName().equals(name)) {
                    try (InputStream is = getResourceAsStream(name.replace('.',
                            '/') + ".class")) {
                        int read;
                        byte[] buffer = new byte[128];
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        while ((read = is.read(buffer)) > 0) {
                            os.write(buffer, 0, read);
                        }
                        data = os.toByteArray();
                        return defineClass(name, data, 0, data.length);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else
                    return super.findClass(name);
            }
        };
    }

    /**
     * Helper class loaded with the {@link CodeBootstrapClassLoader}, causing
     * deserialization to use that class loader too.
     */
    private static class DeserializationHelper implements
            Function<byte[], Object> {
        @SuppressWarnings("unused")
        public DeserializationHelper() {
        }

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

    public CodeRunnerCommon.Response handleRequest(CodeRunnerCommon.Request req) {
        log.debug("Handling " + req.getClass().getSimpleName());
        if (req instanceof CodeRunnerCommon.CustomRequest) {
            CustomRequest customRequest = (CustomRequest) req;
            RemoteCode remoteCode = remoteCodes.get(customRequest.runId);
            if (remoteCode != null)
                return new CodeRunnerCommon.CustomResponse(
                        remoteCode.handle(customRequest.payload));
            else
                return new CodeRunnerCommon.FailureResponse(
                        new RuntimeException("Code already completed"));
        } else if (req instanceof RunCodeRequest) {
            RunCodeRequest runRequest = (RunCodeRequest) req;
            long codeId = nextCodeId.getAndIncrement();
            CodeBootstrapClassLoader cl = new CodeBootstrapClassLoader(
                    runRequest.bootstrapClasses);

            RemoteCode remoteCode;
            try {
                Class<?> cls = cl.findClass(DeserializationHelper.class
                        .getName());
                Constructor<?> constructor = cls.getDeclaredConstructor();
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked")
                Function<byte[], Object> deserializationHelper = (Function<byte[], Object>) constructor
                        .newInstance();
                remoteCode = (RemoteCode) deserializationHelper
                        .apply(((RunCodeRequest) req).code);
            } catch (Exception e) {
                return new CodeRunnerCommon.FailureResponse(e);
            }
            remoteCodes.put(codeId, remoteCode);
            log.debug("invoking code");
            executor.execute(() -> {
                try {
                    remoteCode.run();
                } finally {
                    remoteCodes.remove(codeId);
                }
            });
            log.debug("sending codeStartedResponse. CodeId: " + codeId);
            return new CodeRunnerCommon.CodeStartedResponse(codeId);
        } else
            return new CodeRunnerCommon.FailureResponse(new RuntimeException(
                    "Unknonw request " + req));
    }
}