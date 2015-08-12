package com.github.ruediste.remoteJUnit.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public interface RunNotifierRemote extends Remote {
    void fireTestRunStarted(Description desc) throws RemoteException;

    void fireTestRunFinished(Result result) throws RemoteException;

    void fireTestStarted(Description desc) throws RemoteException;

    void fireTestFailure(Failure failure) throws RemoteException;

    void fireTestAssumptionFailed(Failure failure) throws RemoteException;

    void fireTestIgnored(Description desc) throws RemoteException;

    void fireTestFinished(Description desc) throws RemoteException;

    void pleaseStop() throws RemoteException;
}
