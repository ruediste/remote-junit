package com.github.ruediste.remoteJUnit.server.rmi;

import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class RemoteJUnitServerLauncher {

    public static void main(String... args) {
        new RemoteJUnitServerLauncher().start();
    }

    public void start() {
        if (System.getSecurityManager() == null) {
            // System.setSecurityManager(new SecurityManager());
        }
        try {
            String name = "com.github.ruediste.remoteJUnit.server";
            JUnitServerRemoteImpl server = new JUnitServerRemoteImpl();
            Remote stub = UnicastRemoteObject.exportObject(server, 0);
            Registry registry = LocateRegistry.createRegistry(2020);
            registry.rebind(name, stub);
            System.out.println("Server Bound");
        } catch (Exception e) {
            System.err.println("JUnit Server exception:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
