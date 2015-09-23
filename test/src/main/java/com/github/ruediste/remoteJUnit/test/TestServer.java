package com.github.ruediste.remoteJUnit.test;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerStandaloneServer;

public class TestServer {

    public static void main(String... args) {
        new CodeRunnerStandaloneServer().startAndWait();
    }
}
