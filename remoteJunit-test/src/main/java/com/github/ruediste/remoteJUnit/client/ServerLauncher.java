package com.github.ruediste.remoteJUnit.client;

import com.github.ruediste.remoteJUnit.server.rmi.RemoteJUnitServerLauncher;

public class ServerLauncher {
    public static void main(String... args) {
        new RemoteJUnitServerLauncher().start();
    }
}
