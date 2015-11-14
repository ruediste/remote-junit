package com.github.ruediste.remoteJUnit.client;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.client.internal.InternalRemoteRunner;
import com.github.ruediste.remoteJUnit.client.internal.Utils;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient;
import com.github.ruediste.remoteJUnit.codeRunner.ParentClassLoaderSupplier;

/**
 * Runs a jUnit test on a remote code runner server.
 * <p>
 * <img src="doc-files/overview.png" alt="class diagram showing an overview over
 * the classes involved in the remote junit client">
 * <p>
 * If the connection to the remote server is not possible, the test is run
 * locally.
 * 
 * @see Remote
 */
public class RemoteTestRunner extends Runner implements Filterable, Sortable {

    private static final Logger log = LoggerFactory
            .getLogger(RemoteTestRunner.class);

    private Runner delegate;

    static class RemoteInfo {
        String endpoint = "http://localhost:4578/";

        Class<? extends Runner> runnerClass = JUnit4.class;

        Class<? extends ParentClassLoaderSupplier> parentClassloaderSupplier = null;

        boolean allowLocalExecution = true;

    }

    RemoteInfo calculateRemoteInfo(Class<?> cls) {
        if (cls == null) {
            return new RemoteInfo();
        }

        RemoteInfo info = calculateRemoteInfo(cls.getSuperclass());
        Remote remote = cls.getAnnotation(Remote.class);
        if (remote != null) {
            if (!"".equals(remote.endpoint()))
                info.endpoint = remote.endpoint();
            if (!Runner.class.equals(remote.runnerClass()))
                info.runnerClass = remote.runnerClass();
            if (!ParentClassLoaderSupplier.class
                    .equals(remote.parentClassloaderSupplier()))
                info.parentClassloaderSupplier = remote
                        .parentClassloaderSupplier();
            if (remote.allowLocalExecution().length > 0) {
                info.allowLocalExecution = remote.allowLocalExecution()[0];
            }
        }
        return info;

    }

    public RemoteTestRunner(Class<?> clazz) throws InitializationError {
        RemoteInfo info = calculateRemoteInfo(clazz);

        {
            String endpoint = System.getProperty("junit.remote.endpoint");
            if (endpoint != null && !endpoint.isEmpty())
                info.endpoint = endpoint;
        }

        delegate = null;

        if (!"-".equals(info.endpoint)) {

            log.debug("Trying remote server {} with runner {}", info.endpoint,
                    info.runnerClass.getName());
            CodeRunnerClient client = new CodeRunnerClient(info.endpoint);
            if (client.isConnectionWorking()) {
                delegate = new InternalRemoteRunner(clazz, client,
                        info.runnerClass, info.parentClassloaderSupplier);
            }
        }

        if (delegate == null && info.allowLocalExecution) {
            delegate = Utils.createRunner(info.runnerClass, clazz);
        }
    }

    @Override
    public Description getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void run(RunNotifier notifier) {
        delegate.run(notifier);
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        Utils.filter(delegate, filter);
    }

    @Override
    public void sort(Sorter sorter) {
        Utils.sort(delegate, sorter);
    }

}
