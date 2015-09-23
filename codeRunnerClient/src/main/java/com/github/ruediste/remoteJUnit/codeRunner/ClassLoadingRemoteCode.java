package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.CustomMessageWrapper;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.EmptyResponse;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.RequestResourceMessage;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.ServerCodeExited;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.ToClientMessagesResponse;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.FailureResponse;

class ClassLoadingRemoteCode<TMessage> implements CodeRunnerCommon.RemoteCode,
        ClassLoadingRemoteCodeRunnerClient.RemoteCodeEnvironment<TMessage> {

    private static final long serialVersionUID = 1L;

    private static class RemoteClassLoader extends ClassLoader {
        private final class RemoteURLStreamHandler extends URLStreamHandler {
            private byte[] bb;

            private final class RemoteURLConnection extends URLConnection {

                private RemoteURLConnection(URL url) {
                    super(url);
                }

                @Override
                public void connect() throws IOException {
                    // NOP
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(bb);
                }
            }

            public RemoteURLStreamHandler(byte[] bb) {
                this.bb = bb;
            }

            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return new RemoteURLConnection(u);
            }
        }

        private static final Logger log = LoggerFactory
                .getLogger(ClassLoadingRemoteCode.RemoteClassLoader.class);

        static {
            registerAsParallelCapable();
        }

        private ClassLoadingRemoteCode<?> code;

        Map<String, Optional<byte[]>> resources = new HashMap<>();

        RemoteClassLoader(ClassLoadingRemoteCode<?> code, ClassLoader parent) {
            super(parent);
            this.code = code;
        }

        private Optional<byte[]> getResourceAsBytes(String name) {

            Optional<byte[]> data;
            synchronized (resources) {
                data = resources.get(name);
            }
            if (data == null) {
                log.debug("requesting from client: " + name);
                // request class
                try {
                    code.toClient.put(new RequestResourceMessage(name));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // wait for class to be returned
                long start = System.currentTimeMillis();
                do {
                    synchronized (resources) {
                        data = resources.get(name);
                        if (data == null)
                            try {
                                resources.wait(500);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                    }
                    if (System.currentTimeMillis() > start + 2000) {
                        // 2 seconds elapsed, abort
                        log.debug("loading aborted: " + name);
                        throw new RuntimeException(
                                "Resource loading timed out. Resource Name: "
                                        + name);
                    }
                } while (data == null);
                log.debug("loaded from client: " + name);
            }
            return data;
        }

        @Override
        public URL getResource(String name) {
            URL result = super.getResource(name);
            if (result == null) {
                Optional<byte[]> resourceAsBytes = getResourceAsBytes(name);
                if (resourceAsBytes.isPresent()) {

                }
                result = resourceAsBytes.map(bb -> {
                    try {

                        return new URL("remoteUrl", "localhost", 0, "name",
                                new RemoteURLStreamHandler(bb));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).orElse(null);
            }
            return result;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream result = super.getResourceAsStream(name);
            if (result == null) {
                result = getResourceAsBytes(name).map(ByteArrayInputStream::new)
                        .orElse(null);
            }
            return result;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith(
                    ClassLoadingRemoteCodeRunnerClient.class.getName()))
                return getClass().getClassLoader().loadClass(name);
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Optional<byte[]> data = getResourceAsBytes(
                    name.replace('.', '/') + ".class");
            byte[] bb = data
                    .orElseThrow(() -> new ClassNotFoundException(name));
            {
                int idx = name.lastIndexOf('.');
                if (idx != -1) {
                    String pkgname = name.substring(0, idx);

                    try {
                        definePackage(pkgname, null, null, null, null, null,
                                null, null);
                    } catch (IllegalArgumentException e) {
                        // package was already defined by parallel thred,
                        // swallow.
                    }
                }
            }
            return defineClass(name, bb, 0, bb.length);
        }

        public void addResource(
                ClassLoadingRemoteCodeRunnerClient.SendResourceMessage msg) {
            synchronized (resources) {
                resources.put(msg.name, Optional.ofNullable(msg.data));
                resources.notifyAll();
            }
        }

        public void addJars(List<byte[]> jars) {
            HashMap<String, Optional<byte[]>> tmp = new HashMap<>();
            for (byte[] jar : jars) {
                try (JarInputStream in = new JarInputStream(
                        new ByteArrayInputStream(jar))) {
                    JarEntry nextJarEntry;
                    while ((nextJarEntry = in.getNextJarEntry()) != null) {
                        tmp.put(nextJarEntry.getName(),
                                Optional.of(ClassLoadingRemoteCodeRunnerClient
                                        .toByteArray(in)));
                    }

                } catch (IOException e) {
                    throw new RuntimeException("Error while reading jar files",
                            e);
                }
            }
            synchronized (resources) {
                resources.putAll(tmp);
                resources.notifyAll();
            }
        }
    }

    private ClassLoadingRemoteCode.RemoteClassLoader classLoader;

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private final BlockingQueue<TMessage> toServer = new LinkedBlockingQueue<>();
    private final BlockingQueue<ClassLoadingRemoteCodeRunnerClient.RemoteCodeMessage> toClient = new LinkedBlockingQueue<ClassLoadingRemoteCodeRunnerClient.RemoteCodeMessage>();

    private ParentClassLoaderSupplier parentClassLoaderSupplier;

    private byte[] codeDelegate;
    transient ExecutorService toServerDeserializer;

    public ClassLoadingRemoteCode(
            Consumer<ClassLoadingRemoteCodeRunnerClient.RemoteCodeEnvironment<TMessage>> codeDelegate) {
        this.codeDelegate = SerializationHelper.toByteArray(codeDelegate);

    }

    private ClassLoader getParentClassLoader() {
        if (parentClassLoaderSupplier == null)
            return Thread.currentThread().getContextClassLoader();
        else
            return parentClassLoaderSupplier.getParentClassLoader();
    }

    Semaphore exitConfirmationReceived = new Semaphore(0);

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        toServerDeserializer = Executors.newSingleThreadExecutor();
        try {
            classLoader = new RemoteClassLoader(this, getParentClassLoader());
            ((Consumer<ClassLoadingRemoteCodeRunnerClient.RemoteCodeEnvironment<TMessage>>) SerializationHelper
                    .toObject(codeDelegate, classLoader)).accept(this);
            toClient.add(new ServerCodeExited(null));
        } catch (Throwable e) {
            ClassLoadingRemoteCodeRunnerClient.log.info("Error occurred: ", e);
            toClient.add(new ServerCodeExited(e));
        } finally {
            try {
                exitConfirmationReceived.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public byte[] handle(byte[] requestBa) {
        ClassLoadingRemoteCodeRunnerClient.RemoteCodeRequest request = (ClassLoadingRemoteCodeRunnerClient.RemoteCodeRequest) SerializationHelper
                .toObject(requestBa, getClass().getClassLoader());
        ClassLoadingRemoteCodeRunnerClient.log.debug("handling {}", request);
        try {
            return SerializationHelper.toByteArray(handle(request));
        } catch (Exception e) {
            return SerializationHelper.toByteArray(new FailureResponse(e));
        } finally {
            ClassLoadingRemoteCodeRunnerClient.log.debug(
                    "handling " + request.getClass().getSimpleName() + " done");
        }
    }

    @SuppressWarnings("unchecked")
    private ClassLoadingRemoteCodeRunnerClient.RemoteCodeResponse handle(
            ClassLoadingRemoteCodeRunnerClient.RemoteCodeRequest request) {
        if (request instanceof ClassLoadingRemoteCodeRunnerClient.GetToClientMessagesRequest) {
            List<ClassLoadingRemoteCodeRunnerClient.RemoteCodeMessage> messages = new ArrayList<>();
            try {
                messages.add(toClient.take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            toClient.drainTo(messages);
            ClassLoadingRemoteCodeRunnerClient.log
                    .debug("sending to client: {}", messages);
            return new ToClientMessagesResponse(messages);
        } else
            if (request instanceof ClassLoadingRemoteCodeRunnerClient.SendToServerMessagesRequest) {
            ClassLoadingRemoteCodeRunnerClient.SendToServerMessagesRequest sendToServerMessagesRequest = (ClassLoadingRemoteCodeRunnerClient.SendToServerMessagesRequest) request;
            for (ClassLoadingRemoteCodeRunnerClient.RemoteCodeMessage message : sendToServerMessagesRequest.messages) {
                ClassLoadingRemoteCodeRunnerClient.log
                        .debug("handling toServer message " + message);
                if (message instanceof ClassLoadingRemoteCodeRunnerClient.ServerCodeExitReceived) {
                    toServerDeserializer.shutdown();
                    exitConfirmationReceived.release();
                } else
                    if (message instanceof ClassLoadingRemoteCodeRunnerClient.SendResourceMessage) {
                    classLoader.addResource(
                            (ClassLoadingRemoteCodeRunnerClient.SendResourceMessage) message);
                } else if (message instanceof ClassLoadingRemoteCodeRunnerClient.SendJarsMessage) {
                    classLoader.addJars(
                            ((ClassLoadingRemoteCodeRunnerClient.SendJarsMessage) message).jars);
                } else
                    if (message instanceof ClassLoadingRemoteCodeRunnerClient.CustomMessageWrapper) {
                    toServerDeserializer.execute(() -> {
                        ClassLoadingRemoteCodeRunnerClient.CustomMessageWrapper wrapper = (ClassLoadingRemoteCodeRunnerClient.CustomMessageWrapper) message;
                        TMessage wrappedMessage = (TMessage) SerializationHelper
                                .toObject(wrapper.message, classLoader);
                        ClassLoadingRemoteCodeRunnerClient.log.debug(
                                "received and deserialized custom message {}",
                                wrappedMessage);
                        toServer.add(wrappedMessage);
                    });
                } else
                    throw new UnsupportedOperationException(
                            "Unknown message " + message);
            }

            return new EmptyResponse();
        } else
            throw new UnsupportedOperationException(
                    request.getClass().getName());

    }

    public ParentClassLoaderSupplier getParentClassLoaderSupplier() {
        return parentClassLoaderSupplier;
    }

    public void setParentClassLoaderSupplier(
            ParentClassLoaderSupplier parentClassLoaderSupplier) {
        this.parentClassLoaderSupplier = parentClassLoaderSupplier;
    }

    @Override
    public BlockingQueue<TMessage> getToServerMessages() {
        return toServer;
    }

    @Override
    public void sendToClient(TMessage msg) {
        toClient.add(
                new CustomMessageWrapper(SerializationHelper.toByteArray(msg)));
    }
}