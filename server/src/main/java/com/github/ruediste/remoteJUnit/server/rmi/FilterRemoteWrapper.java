package com.github.ruediste.remoteJUnit.server.rmi;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import com.github.ruediste.remoteJUnit.common.rmi.FilterRemote;

public class FilterRemoteWrapper extends Filter {

    private FilterRemote delegate;

    public FilterRemoteWrapper(FilterRemote delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean shouldRun(Description description) {
        return delegate.shouldRun(description);
    }

    @Override
    public String describe() {
        return delegate.describe();
    }

}
