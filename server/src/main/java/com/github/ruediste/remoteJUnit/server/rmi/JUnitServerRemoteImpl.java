package com.github.ruediste.remoteJUnit.server.rmi;

import org.junit.runner.Runner;

import com.github.ruediste.remoteJUnit.common.Utils;
import com.github.ruediste.remoteJUnit.common.rmi.JUnitServerRemote;
import com.github.ruediste.remoteJUnit.common.rmi.RunnerRemote;

public class JUnitServerRemoteImpl implements JUnitServerRemote {

    @Override
    public RunnerRemote createRunner(Class<? extends Runner> runnerClass,
            Class<?> testClass) {
        return new RunnerRemoteImpl(Utils.createRunner(runnerClass, testClass));
    }

}
