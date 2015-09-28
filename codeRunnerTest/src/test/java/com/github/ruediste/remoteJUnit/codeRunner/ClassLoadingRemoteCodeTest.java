package com.github.ruediste.remoteJUnit.codeRunner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.MessageHandlingEnvironment;

public class ClassLoadingRemoteCodeTest {

    private final static class ServerCode
            implements MessageHandlingServerCode<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(MessageHandlingEnvironment<String> env) {
            env.sendToClient("hello");
            try {
                assertEquals("helloFromClient",
                        env.getToServerMessages().take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test() {
        ClassLoadingCodeRunnerClient<String> client = new ClassLoadingCodeRunnerClient<>();
        client.runCode(new ServerCode(), (msg, sender) -> {
            assertEquals("hello", msg);
            sender.accept("helloFromClient");
        });
    }
}
