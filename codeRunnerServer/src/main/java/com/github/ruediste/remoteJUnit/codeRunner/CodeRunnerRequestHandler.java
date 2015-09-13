package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.Code;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.CustomRequest;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.Request;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.RunCodeRequest;

public class CodeRunnerRequestHandler {

    private final static Logger log = LoggerFactory
            .getLogger(CodeRunnerRequestHandler.class);

    private AtomicLong nextCodeId = new AtomicLong(0);
    private ConcurrentMap<Long, Code> codes = new ConcurrentHashMap<>();

    private Executor executor;

    public CodeRunnerRequestHandler() {
        this(Executors.newCachedThreadPool()::execute);
    }

    /**
     * @param executor
     *            Used to execute the supplied {@link Code}s. The executor MUST
     *            return immediately, starting the supplied runnable in a
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
        protected java.lang.Class<?> findClass(String name)
                throws ClassNotFoundException {
            byte[] data = classData.get(name);
            if (data != null) {
                return defineClass(name, data, 0, data.length);
            } else
                return super.findClass(name);
        };
    }

    public CodeRunnerCommon.Response handleRequest(CodeRunnerCommon.Request req) {
        if (req instanceof CodeRunnerCommon.CustomRequest) {
            CustomRequest customRequest = (CustomRequest) req;
            return null;
        } else if (req instanceof RunCodeRequest) {
            RunCodeRequest runRequest = (RunCodeRequest) req;
            long codeId = nextCodeId.getAndIncrement();
            CodeBootstrapClassLoader cl = new CodeBootstrapClassLoader(
                    runRequest.bootstrapClasses);
            Code code;
            try (ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(((RunCodeRequest) req).code)) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    try {
                        return cl.loadClass(desc.getName());
                    } catch (ClassNotFoundException e) {
                        return super.resolveClass(desc);
                    }
                }
            }) {
                code = (Code) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                return new CodeRunnerCommon.FailureResponse(e);
            }
            codes.put(codeId, code);
            executor.execute(() -> {
                try {
                    code.run();
                } finally {
                    codes.remove(codeId);
                }
            });
            return new CodeRunnerCommon.CodeStartedResponse(codeId);
        } else
            return new CodeRunnerCommon.FailureResponse(new RuntimeException(
                    "Unknonw request " + req));
    }
}
