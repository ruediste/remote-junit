package com.github.ruediste.remoteJUnit.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RemoteTestRunner.class)
public class TestRemoteRunning {

    @Test
    public void test() throws Throwable {

        System.out.print("Hello");
        System.err.print("world");
    }

    @Test
    public void failing() throws Throwable {
        fail();
    }

    @Test
    public void referencedClass() throws Throwable {
        assertEquals("fooo", new ReferencedFromTest().getValue());
    }
}
