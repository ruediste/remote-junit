package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.CodeStartedResponse;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.CustomResponse;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.FailureResponse;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.Response;

public class CodeRunnerClient {
    private static final Logger log = LoggerFactory
            .getLogger(CodeRunnerClient.class);

    private Function<byte[], byte[]> requestSender;

    public CodeRunnerClient() {
        this("http://localhost:4578");
    }

    public CodeRunnerClient(String endPoint) {
        this(new HttpSender(endPoint));
    }

    public CodeRunnerClient(Function<byte[], byte[]> messageSender) {
        this.requestSender = messageSender;
    }

    public static class RequestChannel {
        private long runId;
        private Function<byte[], byte[]> requestSender;

        public RequestChannel(long runId,
                Function<byte[], byte[]> requestSender) {
            this.runId = runId;
            this.requestSender = requestSender;
        }

        public Object sendRequest(Object request) {
            log.debug("sending custom request " + request.getClass().getName());
            CodeRunnerCommon.CustomResponse resp = (CustomResponse) toResponse(
                    requestSender.apply(SerializationHelper.toByteArray(
                            new CodeRunnerCommon.CustomRequest(runId,
                                    SerializationHelper
                                            .toByteArray(request)))));
            return SerializationHelper.toObject(resp.payload);
        }
    }

    public static class ClassMapBuilder {
        public Map<String, byte[]> map = new HashMap<>();

        /**
         * Add the specified class along with all inner classes
         */
        public ClassMapBuilder addClass(Class<?>... classes) {
            for (Class<?> cls : classes)
                if (cls != null && addClassImpl(cls)) {
                    for (Class<?> inner : cls.getDeclaredClasses()) {
                        if (Objects.equals(inner.getEnclosingClass(), cls))
                            addClass(inner);
                    }
                }
            return this;
        }

        private boolean addClassImpl(Class<?> cls) {
            if (map.containsKey(cls.getName()))
                return false;
            try (InputStream in = cls.getClassLoader().getResourceAsStream(
                    cls.getName().replace('.', '/') + ".class")) {
                if (in == null)
                    throw new RuntimeException(
                            "Cannot read .class file of " + cls.getName());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                transfer(in, out);
                out.close();
                map.put(cls.getName(), out.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(
                        "Error while reading " + cls.getName(), e);
            }
            return true;
        }

    }

    private static void transfer(InputStream in, OutputStream out) {
        byte[] buffer = new byte[1024];
        int read;
        try {
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public RequestChannel startCode(CodeRunnerCommon.RemoteCode remoteCode,
            ClassMapBuilder bootstrapClasses) {
        log.debug("starting " + remoteCode.getClass().getName());
        CodeRunnerCommon.CodeStartedResponse response = (CodeStartedResponse) toResponse(
                requestSender.apply(SerializationHelper
                        .toByteArray(new CodeRunnerCommon.RunCodeRequest(
                                SerializationHelper.toByteArray(remoteCode),
                                bootstrapClasses.map))));
        return new RequestChannel(response.runId, requestSender);
    }

    private static CodeRunnerCommon.Response toResponse(byte[] bytes) {
        Response resp = (Response) SerializationHelper.toObject(bytes);
        if (resp instanceof FailureResponse) {
            throw new RuntimeException(((FailureResponse) resp).exception);
        }
        return resp;
    }

    public Function<byte[], byte[]> getRequestSender() {
        return requestSender;
    }

    public void setMessageSender(Function<byte[], byte[]> messageSender) {
        this.requestSender = messageSender;
    }

    private static class HttpSender implements Function<byte[], byte[]> {
        String endpoint;

        public HttpSender(String endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public byte[] apply(byte[] request) {
            try {

                URL serverUrl = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) serverUrl
                        .openConnection();

                // send request
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type",
                        "application/octet-stream");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(request);
                }

                // read response
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    transfer(in, baos);
                    baos.close();
                    return baos.toByteArray();
                }

            } catch (ConnectException e) {
                throw new RuntimeException("Error connection to " + endpoint,
                        e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
