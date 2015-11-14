package com.github.ruediste.remoteJUnit.codeRunner;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient.ClassMapBuilder;

/**
 * Helper to trigger the exit of a remote server
 */
public class ServerExitTriggerer {

    private static final class Code implements RequestHandlingServerCode {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    // NOP
                }
            }
        }

        @Override
        public void initialize() {
        }

        @Override
        public byte[] handle(byte[] request) {
            System.exit(0);
            return null;
        }
    }

    public static void main(String... args) {
        String url;
        if (args.length == 0) {
            url = "http://localhost:4578";
        } else if (args.length == 1) {
            url = args[0];
        } else
            throw new RuntimeException(
                    "Invoke with zero or exactly one argument, which is the url of the code runner server");
        RequestChannel channel = new CodeRunnerClient(url).startCode(new Code(),
                new ClassMapBuilder().addClass(Code.class));
        try {
            channel.sendRequest("");
        } catch (Exception e) {
            // swallow
        }
    }
}
