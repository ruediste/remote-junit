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

import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.CustomRequest;
import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.Request;
import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.RunCodeRequest;

/**
 * Implements a server accepting code and executing it
 */
public class CodeRunnerRequestHandler {

    private final static Logger log = LoggerFactory
            .getLogger(CodeRunnerRequestHandler.class);

    private AtomicLong nextCodeId = new AtomicLong(0);
    private ConcurrentMap<Long, ServerCode> remoteCodes = new ConcurrentHashMap<>();

    private Executor executor;

    private ClassLoader parentClassLoader;

    public CodeRunnerRequestHandler() {
        this(CodeRunnerRequestHandler.class.getClassLoader(),
                Executors.newCachedThreadPool()::execute);
    }

    /**
     * @param executor
     *            Used to execute the supplied {@link ServerCode}s. The executor
     *            MUST return immediately, starting the supplied runnable in a
     *            separate thread.
     */
    public CodeRunnerRequestHandler(ClassLoader parentClassLoader,
            Executor executor) {
        this.parentClassLoader = parentClassLoader;
        this.executor = executor;
    }

    public byte[] handle(byte[] request) throws IOException {
        try (InputStream in = new ByteArrayInputStream(request)) {
            return handle(in);
        }
    }

    public byte[] handle(InputStream in) throws IOException {
        ObjectInputStream oin = new ObjectInputStream(in);
        RemoteCodeRunnerRequestsAndResponses.Request req = null;
        try {
            req = (Request) oin.readObject();
        } catch (ClassNotFoundException e) {
            log.error("Error while parsing request", e);
            return toByteArray(
                    new RemoteCodeRunnerRequestsAndResponses.FailureResponse(
                            e));
        }

        return toByteArray(handleRequest(req));

    }

    private static byte[] toByteArray(Object resp) throws IOException {
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

        private CodeBootstrapClassLoader(ClassLoader parent,
                Map<String, byte[]> classData) {
            super(parent);
            this.classData = classData;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (name.endsWith(".class")) {
                byte[] data = classData.get(
                        name.substring(0, name.length() - ".class".length())
                                .replace('/', '.'));
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
                    try (InputStream is = DeserializationHelper.class
                            .getClassLoader().getResourceAsStream(
                                    name.replace('.', '/') + ".class")) {
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
    private static class DeserializationHelper
            implements Function<byte[], Object> {
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

    RemoteCodeRunnerRequestsAndResponses.Response handleRequest(
            RemoteCodeRunnerRequestsAndResponses.Request req) {
        log.debug("Handling " + req.getClass().getSimpleName());
        if (req instanceof RemoteCodeRunnerRequestsAndResponses.CustomRequest) {
            CustomRequest customRequest = (CustomRequest) req;
            ServerCode remoteCode = remoteCodes.get(customRequest.runId);
            if (remoteCode != null)
                return new RemoteCodeRunnerRequestsAndResponses.CustomResponse(
                        remoteCode.handle(customRequest.payload));
            else
                return new RemoteCodeRunnerRequestsAndResponses.FailureResponse(
                        new RuntimeException("Code already completed"));
        } else if (req instanceof RunCodeRequest) {
            RunCodeRequest runRequest = (RunCodeRequest) req;
            long codeId = nextCodeId.getAndIncrement();

            CodeBootstrapClassLoader cl;
            if (parentClassLoader == null)
                cl = new CodeBootstrapClassLoader(runRequest.bootstrapClasses);
            else
                cl = new CodeBootstrapClassLoader(parentClassLoader,
                        runRequest.bootstrapClasses);

            ServerCode remoteCode;
            try {
                Class<?> cls = cl
                        .findClass(DeserializationHelper.class.getName());
                Constructor<?> constructor = cls.getDeclaredConstructor();
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked")
                Function<byte[], Object> deserializationHelper = (Function<byte[], Object>) constructor
                        .newInstance();
                remoteCode = (ServerCode) deserializationHelper
                        .apply(((RunCodeRequest) req).code);
            } catch (Exception e) {
                return new RemoteCodeRunnerRequestsAndResponses.FailureResponse(
                        e);
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
            return new RemoteCodeRunnerRequestsAndResponses.CodeStartedResponse(
                    codeId);
        } else
            return new RemoteCodeRunnerRequestsAndResponses.FailureResponse(
                    new RuntimeException("Unknonw request " + req));
    }
}
