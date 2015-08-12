package com.github.ruediste.remoteJUnit.server.rmi;

import java.rmi.RemoteException;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import com.github.ruediste.remoteJUnit.common.rmi.RunNotifierRemote;

public class RunNotifierRemoteWrapper extends RunNotifier {

    private RunNotifierRemote delegate;

    public RunNotifierRemoteWrapper(RunNotifierRemote delegate) {
        this.delegate = delegate;
    }

    @Override
    public void fireTestRunStarted(Description desc) {
        try {
            delegate.fireTestRunStarted(desc);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fireTestRunFinished(Result result) {
        try {
            delegate.fireTestRunFinished(result);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fireTestStarted(Description desc) {
        try {
            delegate.fireTestStarted(desc);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fireTestFailure(Failure failure) {
        try {
            delegate.fireTestFailure(failure);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fireTestAssumptionFailed(Failure failure) {
        try {
            delegate.fireTestAssumptionFailed(failure);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fireTestIgnored(Description desc) {
        try {
            delegate.fireTestIgnored(desc);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fireTestFinished(Description desc) {
        try {
            delegate.fireTestFinished(desc);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

}
