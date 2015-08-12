package com.github.ruediste.remoteJUnit.server.rmi;

import java.util.Comparator;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Sorter;

import com.github.ruediste.remoteJUnit.common.rmi.SorterRemote;

public class SorterRemoteWrapper extends Sorter {

    public SorterRemoteWrapper(final SorterRemote delegate) {
        super(new Comparator<Description>() {

            @Override
            public int compare(Description o1, Description o2) {
                return delegate.compare(o1, o2);
            }
        });
    }

}
