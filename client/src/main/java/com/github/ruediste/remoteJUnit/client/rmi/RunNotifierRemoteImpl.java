package com.github.ruediste.remoteJUnit.client.rmi;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;

import com.github.ruediste.remoteJUnit.common.rmi.RunNotifierRemote;

public class RunNotifierRemoteImpl implements RunNotifierRemote {

    private RunNotifier delegate;

    public RunNotifierRemoteImpl(RunNotifier delegate) {
        this.delegate = delegate;

    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public void fireTestRunStarted(Description description) {
        delegate.fireTestRunStarted(description);
    }

    @Override
    public void fireTestRunFinished(Result result) {
        delegate.fireTestRunFinished(result);
    }

    @Override
    public void fireTestStarted(Description description)
            throws StoppedByUserException {
        delegate.fireTestStarted(description);
    }

    @Override
    public void fireTestFailure(Failure failure) {
        delegate.fireTestFailure(failure);
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public void fireTestAssumptionFailed(Failure failure) {
        delegate.fireTestAssumptionFailed(failure);
    }

    @Override
    public void fireTestIgnored(Description description) {
        delegate.fireTestIgnored(description);
    }

    @Override
    public void fireTestFinished(Description description) {
        delegate.fireTestFinished(description);
    }

    @Override
    public void pleaseStop() {
        delegate.pleaseStop();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
