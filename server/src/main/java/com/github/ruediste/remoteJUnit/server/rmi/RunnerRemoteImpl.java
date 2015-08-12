package com.github.ruediste.remoteJUnit.server.rmi;

import java.io.PrintStream;
import java.rmi.RemoteException;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;

import com.github.ruediste.remoteJUnit.common.OutErrCombiningStream;
import com.github.ruediste.remoteJUnit.common.rmi.FilterRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunNotifierRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;
import com.github.ruediste.remoteJUnit.common.rmi.SorterRemote;
import com.github.ruediste.remoteJUnit.server.RedirectingStream;

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
    public OutErrCombiningStream run(final RunNotifierRemote notifier)
            throws RemoteException {
        return catchOutput(new Runnable() {

            @Override
            public void run() {
                delegate.run(new RunNotifierRemoteWrapper(notifier));
            }
        });
    }

    private static OutErrCombiningStream catchOutput(Runnable task) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        OutErrCombiningStream combination = new OutErrCombiningStream();
        RedirectingStream out = new RedirectingStream(origOut,
                combination.getOut());
        RedirectingStream err = new RedirectingStream(origErr,
                combination.getErr());

        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        try {
            task.run();
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
        combination.commitLastEntry();
        return combination;
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
