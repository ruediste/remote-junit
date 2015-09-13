package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class CodeRunnerStandaloneServer extends NanoHTTPD {
    public CodeRunnerStandaloneServer(int port) {
        super(port);
    }

    private static final Logger log = LoggerFactory
            .getLogger(CodeRunnerStandaloneServer.class);

    public static void main(String[] args) {
        startServerAndWait();
    }

    public static void startServerAndWait() {
        startServerAndWait(4578);
    }

    private CodeRunnerRequestHandler handler = new CodeRunnerRequestHandler();

    public static void startServerAndWait(int port) {
        try {
            new CodeRunnerStandaloneServer(port).start();
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

            CodeRunnerCommon.Response resp = handler.handle(session
                    .getInputStream());

            return new Response(Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(
                            CodeRunnerRequestHandler.toByteArray(resp)));
        } catch (IOException e) {
            return new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "Error: " + e.getMessage());
        }

    }

}
