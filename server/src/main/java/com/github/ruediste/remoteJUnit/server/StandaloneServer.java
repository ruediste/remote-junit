package com.github.ruediste.remoteJUnit.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.common.requests.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.common.responses.FailureResponse;
import com.github.ruediste.remoteJUnit.common.responses.RemoteJUnitResponse;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class StandaloneServer extends NanoHTTPD {
    public StandaloneServer(int port) {
        super(port);
    }

    private static final Logger log = LoggerFactory
            .getLogger(StandaloneServer.class);

    public static void main(String[] args) {
        startServerAndWait();
    }

    public static void startServerAndWait() {
        startServerAndWait(4578);
    }

    private RemoteJUnitRequestHandler handler = new RemoteJUnitRequestHandler(
            () -> StandaloneServer.class.getClassLoader());

    public static void startServerAndWait(int port) {
        try {
            new StandaloneServer(port).start();
            log.info("RemotJUnit Test Server Running");
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
            RemoteJUnitResponse resp = null;
            ObjectInputStream in;
            in = new ObjectInputStream(session.getInputStream());

            RemoteJUnitRequest req = null;
            try {
                req = (RemoteJUnitRequest) in.readObject();
            } catch (ClassNotFoundException e) {
                log.error("Error while parsing request", e);
                resp = new FailureResponse(e);
            }

            if (resp == null)
                resp = handler.handle(req);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);
            out.writeObject(resp);
            out.close();
            return new Response(Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException e) {
            return new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "Error: " + e.getMessage());
        }

    }
}
