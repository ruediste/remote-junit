package com.github.ruediste.remoteJUnit.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.server.RMIClassLoaderSpi;

public class RemoteJUnitRMIClassLoaderSPI extends RMIClassLoaderSpi {

    @Override
    public Class<?> loadClass(String codebase, String name,
            ClassLoader defaultLoader) throws MalformedURLException,
            ClassNotFoundException {
        return sun.rmi.server.LoaderHandler.loadClass(codebase, name,
                defaultLoader);
    }

    @Override
    public Class<?> loadProxyClass(String codebase, String[] interfaces,
            ClassLoader defaultLoader) throws MalformedURLException,
            ClassNotFoundException {
        return sun.rmi.server.LoaderHandler.loadProxyClass(codebase,
                interfaces, defaultLoader);
    }

    @Override
    public ClassLoader getClassLoader(String codebase)
            throws MalformedURLException {
        return sun.rmi.server.LoaderHandler.getClassLoader(codebase);
    }

    @SuppressWarnings("restriction")
    @Override
    public String getClassAnnotation(Class<?> cl) {
        String result = sun.rmi.server.LoaderHandler.getClassAnnotation(cl);
        if (result == null && cl.getClassLoader() instanceof URLClassLoader) {
            StringBuilder sb = new StringBuilder();
            for (URL url : ((URLClassLoader) cl.getClassLoader()).getURLs()) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(url.toExternalForm());
            }
            result = sb.toString();
        }
        return result;
    }
}
