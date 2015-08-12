package com.github.ruediste.remoteJUnit.common.rmi;

import java.rmi.Remote;

import org.junit.runner.Description;

public interface FilterRemote extends Remote {

    boolean shouldRun(Description description);

    String describe();
}
