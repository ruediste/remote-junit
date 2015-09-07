package com.github.ruediste.remoteJUnit.client;

import java.net.URLClassLoader;
import java.util.Arrays;

import com.github.ruediste.remoteJUnit.server.rmi.RemoteJUnitServerLauncher;

public class ServerLauncher {
    public static void main(String... args) throws Exception {
        System.out.println(Arrays.asList(((URLClassLoader) ServerLauncher.class
                .getClassLoader()).getURLs()));
        // ServerLauncher.class.getClassLoader().loadClass(

        // "com.github.ruediste.remoteJUnit.client.Test");
        new RemoteJUnitServerLauncher().start();
    }
}
