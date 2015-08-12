package com.github.ruediste.remoteJUnit.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import org.junit.runner.Runner;

public interface JUnitServerRemote extends Remote {

    RunnerRemote createRunner(Class<? extends Runner> runnerClass,
            Class<?> testClass) throws RemoteException;
}
