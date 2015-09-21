package com.github.ruediste.remoteJUnit.test;

import static org.junit.Assert.fail;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;
import org.junit.runner.Result;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.model.InitializationError;

import com.github.ruediste.remoteJUnit.client.RemoteTestRunner;

public class CanStopTest {

    /**
     * {@link CanStopHelperTest} contains two tests, each taking 1s to complete.
     * If both execute, this will take 2s, which triggers the timeout. If the
     * stop works, the 2nd will not be run, causing an execution time of about
     * 1s.
     */
    @Test(timeout = 6000)
    public void runTest_callPleasStop_stopWhenExecutingNewTest()
            throws InitializationError, Exception {
        RunNotifier notifier = new RunNotifier();
        notifier.pleaseStop();
        Result result = new Result();
        notifier.addListener(result.createListener());

        RemoteTestRunner runner = new RemoteTestRunner(CanStopHelperTest.class);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> future = pool.submit(() -> {
            runner.run(notifier);
        });
        try {
            future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof StoppedByUserException) {
                // NOP
            } else if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException) e.getCause();
            else if (e.getCause() instanceof Error)
                throw (Error) e.getCause();
            else
                throw e;
        }

        pool.shutdown();
        if (!result.wasSuccessful()) {
            fail(Objects.toString(result.getFailures()));
        }

    }
}
