package com.github.ruediste.remoteJUnit.client;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.ParentRunner;

import com.github.ruediste.remoteJUnit.client.RemoteTestRunner.RemoteInfo;
import com.github.ruediste.remoteJUnit.codeRunner.ParentClassLoaderSupplier;

public class RemoteTestRunnerTest {

    RemoteTestRunner runner;

    @Before
    public void before() throws Throwable {
        runner = new RemoteTestRunner(A.class);
    }

    private class A {
    }

    @Test
    public void calculateRemoteInfo_noAnnotation_shouldReturnDefaults()
            throws Exception {
        RemoteInfo info = runner.calculateRemoteInfo(A.class);
        assertEquals("http://localhost:4578/", info.endpoint);
        assertEquals(null, info.parentClassloaderSupplier);
        assertEquals(BlockJUnit4ClassRunner.class, info.runnerClass);
    }

    private abstract static class TestParentClassLoaderSupplier implements
            ParentClassLoaderSupplier {
    }

    @Remote(endpoint = "foo", parentClassloaderSupplier = TestParentClassLoaderSupplier.class, runnerClass = ParentRunner.class)
    private class B {
    }

    @Test
    public void calculateRemoteInfo_annotationPresent_shouldReturnAnnotationValues() {
        RemoteInfo info = runner.calculateRemoteInfo(B.class);
        assertEquals("foo", info.endpoint);
        assertEquals(TestParentClassLoaderSupplier.class,
                info.parentClassloaderSupplier);
        assertEquals(ParentRunner.class, info.runnerClass);
    }

    @Remote(endpoint = "bar")
    private class C extends B {
    }

    @Test
    public void calculateRemoteInfo_annotationOnSubclass_shouldSelectivelyOverwrite() {
        RemoteInfo info = runner.calculateRemoteInfo(C.class);
        assertEquals("bar", info.endpoint);
        assertEquals(TestParentClassLoaderSupplier.class,
                info.parentClassloaderSupplier);
        assertEquals(ParentRunner.class, info.runnerClass);
    }
}
