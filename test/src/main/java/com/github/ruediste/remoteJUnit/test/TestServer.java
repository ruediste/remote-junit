package com.github.ruediste.remoteJUnit.test;

import com.github.ruediste.remoteJUnit.server.StandaloneServer;

public class TestServer {

    public static void main(String... args) {
        StandaloneServer.startServerAndWait();
    }
}
