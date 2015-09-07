package com.github.ruediste.remoteJUnit.server.rmi;

import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.Permission;

import com.github.ruediste.remoteJUnit.common.Constants;

public class RemoteJUnitServerLauncher {

    public static void main(String... args) {
        new RemoteJUnitServerLauncher().start();
    }

    public void start() {
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission perm) {
                }

                @Override
                public void checkPermission(Permission perm, Object context) {
                }
            });
        }
        try {
            JUnitServerRemoteImpl server = new JUnitServerRemoteImpl();
            Remote stub = UnicastRemoteObject.exportObject(server, 0);
            Registry registry = LocateRegistry.createRegistry(Constants.PORT);
            registry.rebind(Constants.NAME, stub);
            System.out.println("Server Bound");
        } catch (Exception e) {
            System.err.println("JUnit Server exception:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
