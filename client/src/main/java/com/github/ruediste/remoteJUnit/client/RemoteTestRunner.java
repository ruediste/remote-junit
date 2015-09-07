package com.github.ruediste.remoteJUnit.client;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URLClassLoader;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.Permission;
import java.util.Arrays;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.client.rmi.InternalRemoteRunner;
import com.github.ruediste.remoteJUnit.common.Constants;
import com.github.ruediste.remoteJUnit.common.Utils;
import com.github.ruediste.remoteJUnit.common.rmi.JUnitServerRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;

public class RemoteTestRunner extends Runner implements Filterable, Sortable {

    private static final Logger log = LoggerFactory
            .getLogger(RemoteTestRunner.class);

    private Runner delegate;

    public RemoteTestRunner(Class<?> clazz) throws InitializationError {
        System.out.println(Arrays.asList(((URLClassLoader) clazz
                .getClassLoader()).getURLs()));
        // System.out.println(RMIClassLoader.getClassAnnotation(clazz));

        Remote remote = Utils.findAnnotation(clazz, Remote.class);
        String endpoint = System.getProperty("junit.remote.endpoint");
        ;
        Class<? extends Runner> remoteRunnerClass;
        if (remote != null) {
            if (endpoint == null)
                endpoint = remote.endpoint();
            remoteRunnerClass = remote.runnerClass();
        } else {
            if (endpoint == null)
                endpoint = "http://localhost:4578/";
            remoteRunnerClass = BlockJUnit4ClassRunner.class;
        }
        log.debug("Trying remote server {} with runner {}", endpoint,
                remoteRunnerClass.getName());

        try {
            if (System.getSecurityManager() == null) {
                System.setSecurityManager(new SecurityManager() {
                    @Override
                    public void checkPermission(Permission perm) {
                    }

                    @Override
                    public void checkPermission(Permission perm, Object context) {
                    }
                });
            }

            Registry registry;
            registry = LocateRegistry.getRegistry(Constants.PORT);
            JUnitServerRemote server = (JUnitServerRemote) registry
                    .lookup(Constants.NAME);

            RunnerRemote runner = server.createRunner(remoteRunnerClass, clazz);

            // a little sleep is required, otherwise we get a strange error
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            delegate = new InternalRemoteRunner(runner);
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NotBoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        if (delegate == null) {
            delegate = Utils.createRunner(remoteRunnerClass, clazz);
        }
    }

    private boolean isRemoteUp(String endPoint) {
        URI uri = URI.create(endPoint.trim());

        try {
            Socket s = new Socket(uri.getHost(), uri.getPort());
            s.close();

            return true;
        } catch (IOException e) {
        }

        return false;
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
