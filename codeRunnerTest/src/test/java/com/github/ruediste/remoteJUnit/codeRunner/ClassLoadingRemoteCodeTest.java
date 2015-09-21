package com.github.ruediste.remoteJUnit.codeRunner;

import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.function.Consumer;

import org.junit.Test;

import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.RemoteCodeEnvironment;

public class ClassLoadingRemoteCodeTest {

    private final static class ServerCode
            implements Consumer<RemoteCodeEnvironment<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void accept(RemoteCodeEnvironment<String> env) {
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
        ClassLoadingRemoteCodeRunnerClient<String> client = new ClassLoadingRemoteCodeRunnerClient<>();
        client.runCode(new ServerCode(), (msg, sender) -> {
            assertEquals("hello", msg);
            sender.accept("helloFromClient");
        });
    }
}
