package com.github.ruediste.remoteJUnit.codeRunner.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import org.junit.Test;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient.ClassMapBuilder;
import com.github.ruediste.remoteJUnit.codeRunner.RequestChannel;
import com.github.ruediste.remoteJUnit.codeRunner.RequestHandlingServerCode;
import com.github.ruediste.remoteJUnit.codeRunner.SerializationHelper;

public class ServerTest {

    private static class TestCode implements RequestHandlingServerCode {
        private static final long serialVersionUID = 1L;

        Semaphore blockRun = new Semaphore(0);
        Semaphore blockHandle = new Semaphore(0);

        volatile Boolean isOnServer;

        @Override
        public void run() {
            isOnServer = Server.runningOnServer.get();
            blockHandle.release();
            try {
                blockRun.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public byte[] handle(byte[] request) {
            try {
                blockHandle.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            blockRun.release();
            Function<TestCode, ?> req = (Function<TestCode, ?>) SerializationHelper
                    .toObject(request, getClass().getClassLoader());
            return SerializationHelper.toByteArray(req.apply(this));
        }

        @Override
        public void initialize() {

        }

    }

    private static class IsServerFunction
            implements Function<TestCode, Boolean>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public Boolean apply(TestCode t) {
            return t.isOnServer;
        }

    }

    @Test
    public void testSimpleCode() {
        CodeRunnerClient client = new CodeRunnerClient();
        RequestChannel channel = client.startCode(new TestCode(),
                new ClassMapBuilder().addClass(TestCode.class)
                        .addClass(IsServerFunction.class));
        assertEquals(true, channel.sendRequest(new IsServerFunction()));
    }

    private static class A {
        private static class AB {
        }
    }

    @Test
    public void testInnerClasses() {
        ClassMapBuilder b = new ClassMapBuilder().addClass(A.class);
        assertTrue(b.map.containsKey(
                com.github.ruediste.remoteJUnit.codeRunner.test.ServerTest.A.AB.class
                        .getName()));
        b = new ClassMapBuilder().addClass(ServerTest.class);
        assertTrue(b.map.containsKey(
                com.github.ruediste.remoteJUnit.codeRunner.test.ServerTest.A.AB.class
                        .getName()));
    }
}
