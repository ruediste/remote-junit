package com.github.ruediste.remoteJUnit.codeRunner;

import java.time.Duration;
import java.time.Instant;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient.ClassMapBuilder;

/**
 * Helper to wait for the startup of a remote server
 */
public class ServerStartupWaiter {

    private static final class Code implements RequestHandlingServerCode {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            // NOP
        }

        @Override
        public void initialize() {
        }

        @Override
        public byte[] handle(byte[] request) {
            return new byte[] {};
        }
    }

    public static void main(String... args) {
        String url;
        Duration timeout;
        if (args.length > 2)
            throw new RuntimeException(
                    "Usage: ServerStartupWaiter [timeout(s) [url]]");
        if (args.length == 0) {
            timeout = Duration.ofSeconds(30);
        } else {
            timeout = Duration.ofSeconds(Long.parseLong(args[0]));
        }

        if (args.length < 2) {
            url = "http://localhost:4578";
        } else
            url = args[0];
        Instant start = Instant.now();

        System.out.println(
                "Waiting " + timeout + " for startup of server " + url);
        while (start.plus(timeout).isBefore(Instant.now())) {
            try {
                RequestChannel channel = new CodeRunnerClient(url).startCode(
                        new Code(), new ClassMapBuilder().addClass(Code.class));
                channel.sendRequest("");
                System.out.println("Server started");
                System.exit(1);
            } catch (Exception e) {
                // swallow
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("Waiting for starup timed out");
        System.exit(1);
    }
}
