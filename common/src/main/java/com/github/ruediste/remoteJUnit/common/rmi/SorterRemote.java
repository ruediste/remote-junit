package com.github.ruediste.remoteJUnit.common.rmi;

import java.rmi.Remote;

import org.junit.runner.Description;

public interface SorterRemote extends Remote {

    int compare(Description o1, Description o2);

}
