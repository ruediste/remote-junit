package com.github.ruediste.remoteJUnit.test;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerStandaloneServer;

public class TestServer {

    public static void main(String... args) {
        CodeRunnerStandaloneServer server = new CodeRunnerStandaloneServer();
        server.startAndWait();
    }
}
