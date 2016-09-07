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
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.CustomMessageWrapper;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.EmptyResponse;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.RequestResourceMessage;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.ServerCodeExited;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.ToClientMessagesResponse;
import com.github.ruediste.remoteJUnit.codeRunner.RemoteCodeRunnerRequestsAndResponses.FailureResponse;

/**
 * Code which is sent to the server and executes the {@link #codeDelegate}.
 */
class ClassLoadingServerCode<TMessage>
        implements RequestHandlingServerCode, ClassLoadingCodeRunnerClient.MessageHandlingEnvironment<TMessage> {

    private static final long serialVersionUID = 1L;

    static class RemoteClassLoader extends ClassLoader {
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

        private static final Logger log = LoggerFactory.getLogger(ClassLoadingServerCode.RemoteClassLoader.class);

        static {
            registerAsParallelCapable();
        }

        private ClassLoadingServerCode<?> code;

        Map<String, Optional<byte[]>> resources = new HashMap<>();

        RemoteClassLoader(ClassLoadingServerCode<?> code, ClassLoader parent) {
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
                        if (data == null) {
                            try {
                                resources.wait(500);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    if (System.currentTimeMillis() > start + 2000) {
                        // 2 seconds elapsed, abort
                        log.debug("loading aborted: " + name);
                        throw new RuntimeException("Resource loading timed out. Resource Name: " + name);
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

                        return new URL("remoteUrl", "localhost", 0, "name", new RemoteURLStreamHandler(bb));
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
                result = getResourceAsBytes(name).map(ByteArrayInputStream::new).orElse(null);
            }
            return result;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(ClassLoadingCodeRunnerClient.class.getName())
                    || name.equals(MessageHandlingServerCode.class.getName())) {
                return getClass().getClassLoader().loadClass(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Optional<byte[]> data = getResourceAsBytes(name.replace('.', '/') + ".class");
            byte[] bb = data.orElseThrow(() -> new ClassNotFoundException(name));
            {
                int idx = name.lastIndexOf('.');
                if (idx != -1) {
                    String pkgname = name.substring(0, idx);

                    try {
                        definePackage(pkgname, null, null, null, null, null, null, null);
                    } catch (IllegalArgumentException e) {
                        // package was already defined by parallel thred,
                        // swallow.
                    }
                }
            }
            return defineClass(name, bb, 0, bb.length);
        }

        public void addResource(ClassLoadingCodeRunnerClient.SendResourceMessage msg) {
            synchronized (resources) {
                resources.put(msg.name, Optional.ofNullable(msg.data));
                resources.notifyAll();
            }
        }

        public void addJars(List<byte[]> jars) {
            HashMap<String, Optional<byte[]>> tmp = new HashMap<>();
            for (byte[] jar : jars) {
                try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(jar))) {
                    JarEntry nextJarEntry;
                    while ((nextJarEntry = in.getNextJarEntry()) != null) {
                        tmp.put(nextJarEntry.getName(), Optional.of(ClassLoadingCodeRunnerClient.toByteArray(in)));
                    }

                } catch (IOException e) {
                    throw new RuntimeException("Error while reading jar files", e);
                }
            }
            synchronized (resources) {
                resources.putAll(tmp);
                resources.notifyAll();
            }
        }
    }

    private ClassLoadingServerCode.RemoteClassLoader classLoader;

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private final BlockingQueue<TMessage> toServer = new LinkedBlockingQueue<>();
    private final BlockingQueue<ClassLoadingCodeRunnerClient.RemoteCodeMessage> toClient = new LinkedBlockingQueue<>();

    private ParentClassLoaderSupplier parentClassLoaderSupplier;

    private byte[] codeDelegate;
    transient ExecutorService toServerDeserializer;

    public ClassLoadingServerCode(MessageHandlingServerCode<TMessage> codeDelegate) {
        this.codeDelegate = SerializationHelper.toByteArray(codeDelegate);

    }

    private ClassLoader getParentClassLoader() {
        if (parentClassLoaderSupplier == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return parentClassLoaderSupplier.getParentClassLoader();
        }
    }

    Semaphore exitConfirmationReceived = new Semaphore(0);

    @Override
    public void initialize() {
        toServerDeserializer = Executors.newSingleThreadExecutor();
        classLoader = new RemoteClassLoader(this, getParentClassLoader());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        try {
            ((MessageHandlingServerCode<TMessage>) SerializationHelper.toObject(codeDelegate, classLoader)).run(this);
            toClient.add(new ServerCodeExited(null));
        } catch (Throwable e) {
            ClassLoadingCodeRunnerClient.log.info("Error occurred: ", e);
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
        ClassLoadingCodeRunnerClient.RemoteCodeRequest request = (ClassLoadingCodeRunnerClient.RemoteCodeRequest) SerializationHelper
            .toObject(requestBa, getClass().getClassLoader());
        ClassLoadingCodeRunnerClient.log.debug("handling {}", request);
        try {
            return SerializationHelper.toByteArray(handle(request));
        } catch (Exception e) {
            return SerializationHelper.toByteArray(new FailureResponse(e));
        } finally {
            ClassLoadingCodeRunnerClient.log.debug("handling " + request.getClass().getSimpleName() + " done");
        }
    }

    @SuppressWarnings("unchecked")
    private ClassLoadingCodeRunnerClient.RemoteCodeResponse handle(
            ClassLoadingCodeRunnerClient.RemoteCodeRequest request) {
        if (request instanceof ClassLoadingCodeRunnerClient.GetToClientMessagesRequest) {
            List<ClassLoadingCodeRunnerClient.RemoteCodeMessage> messages = new ArrayList<>();
            try {
                messages.add(toClient.take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            toClient.drainTo(messages);
            ClassLoadingCodeRunnerClient.log.debug("sending to client: {}", messages);
            return new ToClientMessagesResponse(messages);
        } else if (request instanceof ClassLoadingCodeRunnerClient.SendToServerMessagesRequest) {
            ClassLoadingCodeRunnerClient.SendToServerMessagesRequest sendToServerMessagesRequest = (ClassLoadingCodeRunnerClient.SendToServerMessagesRequest) request;
            for (ClassLoadingCodeRunnerClient.RemoteCodeMessage message : sendToServerMessagesRequest.messages) {
                ClassLoadingCodeRunnerClient.log.debug("handling toServer message " + message);
                if (message instanceof ClassLoadingCodeRunnerClient.ServerCodeExitReceived) {
                    toServerDeserializer.shutdown();
                    exitConfirmationReceived.release();
                } else if (message instanceof ClassLoadingCodeRunnerClient.SendResourceMessage) {
                    classLoader.addResource((ClassLoadingCodeRunnerClient.SendResourceMessage) message);
                } else if (message instanceof ClassLoadingCodeRunnerClient.SendJarsMessage) {
                    classLoader.addJars(((ClassLoadingCodeRunnerClient.SendJarsMessage) message).jars);
                } else if (message instanceof ClassLoadingCodeRunnerClient.CustomMessageWrapper) {
                    toServerDeserializer.execute(() -> {
                        ClassLoadingCodeRunnerClient.CustomMessageWrapper wrapper = (ClassLoadingCodeRunnerClient.CustomMessageWrapper) message;
                        TMessage wrappedMessage = (TMessage) SerializationHelper.toObject(wrapper.message, classLoader);
                        ClassLoadingCodeRunnerClient.log.debug("received and deserialized custom message {}",
                            wrappedMessage);
                        toServer.add(wrappedMessage);
                    });
                } else {
                    throw new UnsupportedOperationException("Unknown message " + message);
                }
            }

            return new EmptyResponse();
        } else {
            throw new UnsupportedOperationException(request.getClass().getName());
        }

    }

    public ParentClassLoaderSupplier getParentClassLoaderSupplier() {
        return parentClassLoaderSupplier;
    }

    public void setParentClassLoaderSupplier(ParentClassLoaderSupplier parentClassLoaderSupplier) {
        this.parentClassLoaderSupplier = parentClassLoaderSupplier;
    }

    @Override
    public BlockingQueue<TMessage> getToServerMessages() {
        return toServer;
    }

    @Override
    public void sendToClient(TMessage msg) {
        toClient.add(new CustomMessageWrapper(SerializationHelper.toByteArray(msg)));
    }
}