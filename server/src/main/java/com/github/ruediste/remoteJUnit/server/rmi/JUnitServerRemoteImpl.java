package com.github.ruediste.remoteJUnit.server.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import org.junit.runner.Runner;

import com.github.ruediste.remoteJUnit.common.Utils;
import com.github.ruediste.remoteJUnit.common.rmi.JUnitServerRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;

public class JUnitServerRemoteImpl implements JUnitServerRemote {

    @Override
    public RunnerRemote createRunner(Class<? extends Runner> runnerClass,
            Class<?> testClass) throws RemoteException {
        RunnerRemoteImpl runnerRemoteImpl = new RunnerRemoteImpl(
                Utils.createRunner(runnerClass, testClass));

        return (RunnerRemote) UnicastRemoteObject.exportObject(
                runnerRemoteImpl, 0);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public RunnerRemote createRunner(String runnerClass, String testClass)
            throws RemoteException {
        try {
            return createRunner((Class) Class.forName(runnerClass),
                    Class.forName(testClass));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
