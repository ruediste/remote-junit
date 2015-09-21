package com.github.ruediste.remoteJUnit.test;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.ruediste.remoteJUnit.client.RemoteTestRunner;

@RunWith(RemoteTestRunner.class)
public class CanStopHelperTest {

    @Test
    public void test1() throws InterruptedException {
        Thread.sleep(4000);
    }

    @Test
    public void test2() throws InterruptedException {
        Thread.sleep(4000);
    }

    @Test
    public void test3() throws InterruptedException {
        Thread.sleep(4000);
    }
}
