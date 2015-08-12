package com.github.ruediste.remoteJUnit.client.rmi;

import java.rmi.RemoteException;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;

import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;

public class InternalRemoteRunner extends Runner implements Sortable,
        Filterable {

    RunnerRemote delegate;

    public InternalRemoteRunner(RunnerRemote delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sort(Sorter sorter) {

    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
    
    }

    @Override
    public Description getDescription() {
        try {
            return delegate.getDescription();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(RunNotifier notifier) {
        try {
            delegate.run(new RunNotifierRemoteImpl(notifier));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

}
