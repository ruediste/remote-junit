package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class CodeRunnerStandaloneServer extends NanoHTTPD {
    private static final int DEFAULT_PORT = 4578;

    public CodeRunnerStandaloneServer() {
        this(DEFAULT_PORT);
    }

    public CodeRunnerStandaloneServer(int port) {
        super(port);
    }

    private static final Logger log = LoggerFactory
            .getLogger(CodeRunnerStandaloneServer.class);

    private CodeRunnerRequestHandler handler;

    @Override
    public void start() throws IOException {
        if (handler == null)
            handler = new CodeRunnerRequestHandler();
        super.start();
    }

    public void startAndWait() {
        try {
            start();
            log.info("CodeRunner Server Running");
            Object obj = new Object();
            synchronized (obj) {
                obj.wait();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {

        try {
            byte[] response = getHandler().handle(session.getInputStream());
            return new Response(Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(response));
        } catch (IOException e) {
            return new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "Error: " + e.getMessage());
        }

    }

    public CodeRunnerRequestHandler getHandler() {
        return handler;
    }

    public void setHandler(CodeRunnerRequestHandler handler) {
        this.handler = handler;
    }

}
