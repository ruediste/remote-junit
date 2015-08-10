package com.github.ruediste.remoteJUnit.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;

import com.github.ruediste.remoteJUnit.messages.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.messages.RemoteJUnitResponse;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class StandaloneServer {

    public static void main(String[] args) throws IOException {
        final RemoteJUnitRequestHandler handler = new RemoteJUnitRequestHandler();
        HttpServer server = HttpServer.create(new InetSocketAddress(9081), 2);
        HttpContext ctx = server.createContext("/");
        ctx.setHandler(new HttpHandler() {

            @Override
            public void handle(HttpExchange t) throws IOException {
                ObjectInputStream in = new ObjectInputStream(t.getRequestBody());
                try {
                    RemoteJUnitRequest req = (RemoteJUnitRequest) in
                            .readObject();
                    RemoteJUnitResponse resp = handler.handle(req);

                    t.sendResponseHeaders(200, 0);
                    ObjectOutputStream os = new ObjectOutputStream(t
                            .getResponseBody());
                    os.writeObject(resp);
                    os.close();
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Error while reading request");
                }
            }
        });
        server.setExecutor(null); // creates a default executor
        server.start();
    }
}
