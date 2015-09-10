package com.github.ruediste.remoteJUnit.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.ruediste.remoteJUnit.client.RemoteTestRunner;

@RunWith(RemoteTestRunner.class)
public class SimpleJUnitTest {

    @Test
    public void simple() {
        assertEquals("foo", "bar");
    }
}
