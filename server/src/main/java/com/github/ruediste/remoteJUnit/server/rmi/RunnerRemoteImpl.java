package com.github.ruediste.remoteJUnit.server.rmi;

import java.rmi.RemoteException;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;

import com.github.ruediste.remoteJUnit.common.rmi.FilterRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunNotifierRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;
import com.github.ruediste.remoteJUnit.common.rmi.SorterRemote;

public class RunnerRemoteImpl implements RunnerRemote {

    private Runner delegate;

    public RunnerRemoteImpl(Runner delegate) {
        this.delegate = delegate;
    }

    @Override
    public Description getDescription() throws RemoteException {
        return delegate.getDescription();
    }

    @Override
    public void run(RunNotifierRemote notifier) throws RemoteException {
        delegate.run(new RunNotifierRemoteWrapper(notifier));
    }

    @Override
    public int testCount() throws RemoteException {
        return delegate.testCount();
    }

    @Override
    public void filter(FilterRemote filter) throws NoTestsRemainException,
            RemoteException {
        ((Filterable) delegate).filter(new FilterRemoteWrapper(filter));
    }

    @Override
    public void sort(SorterRemote sorter) throws RemoteException {
        ((Sortable) delegate).sort(new SorterRemoteWrapper(sorter));
    }

}
