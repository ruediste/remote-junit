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
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.CodeStartedResponse;
import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.FailureResponse;
import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.Response;

/**
 * Client to run {@link RequestHandlingServerCode} on a code runner server.
 * 
 * <p>
 * <img src="doc-files/CodeRunnerClient_sequence.png"/>
 * <p>
 * Using {@link #startCode(RequestHandlingServerCode, ClassMapBuilder)}, a piece
 * of code can be sent to the server where it is started. A session id is
 * returned, which can then be used to send requests to the server code, as long
 * as the code is running.
 * 
 * <p>
 * The transport mechanism is pluggable by using a custom
 * {@link #CodeRunnerClient(Function) message sender}. By default, communication
 * happens via HTTP POST requests.
 */
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

    public RequestChannel startCode(RequestHandlingServerCode remoteCode,
            ClassMapBuilder bootstrapClasses) {
        log.debug("starting " + remoteCode.getClass().getName());
        RemoteCodeRunnerRequestsAndResponses.CodeStartedResponse response = (CodeStartedResponse) toResponse(
                requestSender
                        .apply(SerializationHelper.toByteArray(
                                new RemoteCodeRunnerRequestsAndResponses.RunCodeRequest(
                                        SerializationHelper
                                                .toByteArray(remoteCode),
                                        bootstrapClasses.map))));
        return new RequestChannel(response.runId, requestSender);
    }

    static RemoteCodeRunnerRequestsAndResponses.Response toResponse(
            byte[] bytes) {
        Response resp = (Response) SerializationHelper.toObject(bytes);
        if (resp instanceof FailureResponse) {
            throw new RuntimeException(((FailureResponse) resp).exception);
        }
        return resp;
    }

    public Function<byte[], byte[]> getRequestSender() {
        return requestSender;
    }

    public void setRequestSender(Function<byte[], byte[]> requestSender) {
        this.requestSender = requestSender;
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

    private static class ConnectionTestServerCode
            implements RequestHandlingServerCode {

        private static final long serialVersionUID = 1L;
        transient CountDownLatch latch;

        @Override
        public void initialize() {
            latch = new CountDownLatch(1);
        }

        @Override
        public void run() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                // NOP
            }
        }

        @Override
        public byte[] handle(byte[] request) {
            latch.countDown();
            return request;
        }

    }

    public boolean isConnectionWorking() {
        try {
            RequestChannel channel = startCode(new ConnectionTestServerCode(),
                    new ClassMapBuilder()
                            .addClass(ConnectionTestServerCode.class));
            Object response = channel.sendRequest("ping");
            return "ping".equals(response);
        } catch (Exception e) {
            return false;
        }
    }
}
