package com.github.ruediste.remoteJUnit.client.rmi;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;

import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;

public class RunnerRemoteWrapper extends Runner implements Sortable,
        Filterable {

    private RunnerRemote delegate;

    public RunnerRemoteWrapper(RunnerRemote delegate) {
        this.delegate = delegate;
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        // TODO Auto-generated method stub

    }

    @Override
    public void sort(Sorter sorter) {
        // TODO Auto-generated method stub

    }

    @Override
    public Description getDescription() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void run(RunNotifier notifier) {
        // TODO Auto-generated method stub

    }

}
