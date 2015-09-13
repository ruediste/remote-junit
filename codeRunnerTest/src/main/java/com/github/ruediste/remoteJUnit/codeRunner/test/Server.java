package com.github.ruediste.remoteJUnit.codeRunner.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerRequestHandler;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerStandaloneServer;

public class Server {

    public static ThreadLocal<Boolean> runningOnServer = new ThreadLocal<>();

    public static void main(String... args) {
        CodeRunnerStandaloneServer server = new CodeRunnerStandaloneServer();
        ExecutorService pool = Executors.newCachedThreadPool();
        server.setHandler(new CodeRunnerRequestHandler(r -> pool
                .execute(() -> {
                    try {
                        runningOnServer.set(true);
                        r.run();
                    } finally {
                        runningOnServer.remove();
                    }
                })));
        server.startAndWait();
    }
}
