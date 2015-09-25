package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient.ClassMapBuilder;

/**
 * Client to run {@link MessageHandlingServerCode} with a bi-directional
 * message-channel and remote class loading on the server.
 * 
 * <p>
 * <b>Message sending </b><br>
 * After the ServerCode is started using the {@link CodeRunnerClient}, a thread
 * is started which takes messages from a local queue and sends them to the
 * server code. The thread which initiated the remote code execution enters a
 * loop requesting messages from the server and handling them, until the remote
 * code exited.
 * <p>
 * <img src="doc-files/ClassLoadingCodeRunnerClient_messageing.png"/>
 * 
 * <p>
 * <b> Server Code Exit </b><br>
 * When the server code completes, a message is sent to the client which will
 * confirm reception and return to the caller. Upon reception of the
 * confirmation, the server code exits, too. This makes sure that all messages
 * are exchanged between client and server before exit, since the messages are
 * delivered in order.
 * <p>
 * <img src="doc-files/ClassLoadingCodeRunnerClient_exit.png"/>
 * 
 * <p>
 * <b> Class Loading </b><br>
 * When the {@link MessageHandlingServerCode ServerCode} requires a class, the
 * {@link ClassLoadingServerCode.RemoteClassLoader class loader} first checks if
 * the class can be found on the server. Otherwise it requests the corresponding
 * class from the client using a {@link RequestResourceMessage}. The class
 * loader then enters a loop waiting for the resource to become available.
 * <p>
 * The client checks if the resource is part of a jar. In this case the whole
 * jar is sent ({@link SendJarsMessage}), otherwise only the requested resource
 * ({@link SendResourceMessage}). The message handling code on the server side
 * adds the resource to the class loader. The class loader detects this, loads
 * the class and returns.
 * <p>
 * <img src="doc-files/ClassLoadingCodeRunnerClient_classLoading.png"/>
 */
public class ClassLoadingCodeRunnerClient<TMessage> {
    final static Logger log = LoggerFactory
            .getLogger(ClassLoadingCodeRunnerClient.class);

    interface RemoteCodeMessage extends Serializable {

    }

    public static class RequestResourceMessage implements RemoteCodeMessage {

        private static final long serialVersionUID = 1L;
        public String name;

        public RequestResourceMessage(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "RequestResourceMessage(" + name + ")";
        }
    }

    public static class ServerCodeExited implements RemoteCodeMessage {
        private Throwable exception;

        public ServerCodeExited(Throwable e) {
            this.exception = e;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    public static class ServerCodeExitReceived implements RemoteCodeMessage {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    public static class SendResourceMessage implements RemoteCodeMessage {
        private static final long serialVersionUID = 1L;
        public byte[] data;
        public String name;

        public SendResourceMessage(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    public static class SendJarsMessage implements RemoteCodeMessage {
        private static final long serialVersionUID = 1L;
        public List<byte[]> jars;

        public SendJarsMessage(List<byte[]> jars) {
            this.jars = jars;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    public static class CustomMessageWrapper implements RemoteCodeMessage {
        private static final long serialVersionUID = 1L;

        byte[] message;

        public CustomMessageWrapper(byte[] message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    public interface RemoteCodeResponse extends Serializable {

    }

    public static class EmptyResponse implements RemoteCodeResponse {

        private static final long serialVersionUID = 1L;

    }

    public static class ToClientMessagesResponse implements RemoteCodeResponse {
        private static final long serialVersionUID = 1L;
        public List<RemoteCodeMessage> messages;

        public ToClientMessagesResponse(List<RemoteCodeMessage> messages) {
            this.messages = messages;
        }

    }

    public interface RemoteCodeRequest extends Serializable {
    }

    public static class GetToClientMessagesRequest
            implements RemoteCodeRequest {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return "GetToClientMessagesRequest";
        }
    }

    public static class SendToServerMessagesRequest
            implements RemoteCodeRequest {
        private static final long serialVersionUID = 1L;

        public List<RemoteCodeMessage> messages = new ArrayList<>();

        public SendToServerMessagesRequest(List<RemoteCodeMessage> messages) {
            this.messages = messages;
        }

        @Override
        public String toString() {
            return "SendToServerMessagesRequest(" + messages + ")";
        }
    }

    /**
     * Allows to send and receive messages from/to the client
     */
    public interface MessageHandlingEnvironment<TMessage> {

        void sendToClient(TMessage msg);

        BlockingQueue<TMessage> getToServerMessages();

        /**
         * Return the class loader supporting dynamic loading of client classes
         */
        ClassLoader getClassLoader();

    }

    private final class ToServerSender implements Runnable {
        private BlockingQueue<RemoteCodeMessage> toServer;
        private RequestChannel channel;

        public ToServerSender(BlockingQueue<RemoteCodeMessage> toServer,
                RequestChannel channel) {
            this.toServer = toServer;
            this.channel = channel;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    List<RemoteCodeMessage> messages = new ArrayList<>();
                    messages.add(toServer.take());
                    toServer.drainTo(messages);
                    channel.sendRequest(
                            new SendToServerMessagesRequest(messages));
                    // quit after the exit confirmation has been sent
                    if (messages.stream().anyMatch(
                            m -> m instanceof ServerCodeExitReceived)) {
                        break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static class MessageChannel<TMessage> {
        private final BlockingQueue<TMessage> toClient = new LinkedBlockingQueue<>();
        private final BlockingQueue<RemoteCodeMessage> toServer = new LinkedBlockingQueue<>();

        public BlockingQueue<TMessage> getToClient() {
            return toClient;
        }

        public void sendMessage(TMessage msg) {
            toServer.add(new CustomMessageWrapper(
                    SerializationHelper.toByteArray(msg)));
        }

    }

    private ParentClassLoaderSupplier parentClassLoaderSupplier;
    private CodeRunnerClient client;

    public void runCode(MessageHandlingServerCode<TMessage> code,
            BiConsumer<TMessage, Consumer<TMessage>> clientMessageHandler) {
        runCode(code, clientMessageHandler, new ClassMapBuilder());
    }

    /**
     * @param clientMessageHandler
     *            handler for messages sent from the server back to the client
     */
    @SuppressWarnings("unchecked")
    public void runCode(MessageHandlingServerCode<TMessage> code,
            BiConsumer<TMessage, Consumer<TMessage>> clientMessageHandler,
            ClassMapBuilder bootstrapClasses) {
        ClassLoadingServerCode<TMessage> clCode = new ClassLoadingServerCode<>(
                code);
        if (parentClassLoaderSupplier != null) {
            clCode.setParentClassLoaderSupplier(parentClassLoaderSupplier);
            bootstrapClasses.addClass(parentClassLoaderSupplier.getClass());
        }
        if (client == null) {
            client = new CodeRunnerClient();
        }
        RequestChannel channel = client.startCode(clCode,
                bootstrapClasses.addClass(ClassLoadingServerCode.class,
                        ClassLoadingCodeRunnerClient.class,
                        SerializationHelper.class,
                        MessageHandlingServerCode.class,
                        ParentClassLoaderSupplier.class));

        MessageChannel<TMessage> msgChannel = new MessageChannel<>();

        // start client to server thread
        ToServerSender toServerSender = new ToServerSender(msgChannel.toServer,
                channel);
        new Thread(toServerSender).start();

        // process incoming messages
        messageProcessingLoop: while (true) {
            ToClientMessagesResponse messagesResponse = (ToClientMessagesResponse) channel
                    .sendRequest(new GetToClientMessagesRequest());
            for (RemoteCodeMessage message : messagesResponse.messages) {
                log.debug("handling toClient message " + message);
                if (message instanceof CustomMessageWrapper) {
                    clientMessageHandler.accept(
                            (TMessage) SerializationHelper.toObject(
                                    ((CustomMessageWrapper) message).message),
                            msg -> msgChannel.sendMessage(msg));
                } else if (message instanceof ServerCodeExited) {
                    Throwable exception = ((ServerCodeExited) message).exception;
                    msgChannel.toServer.add(new ServerCodeExitReceived());
                    if (exception != null) {
                        throw new RuntimeException("Error in server code",
                                exception);
                    }

                    break messageProcessingLoop;
                } else if (message instanceof RequestResourceMessage) {
                    RequestResourceMessage sendResourceMessage = (RequestResourceMessage) message;
                    String name = sendResourceMessage.name;
                    log.debug("handling sendResourceMessage for " + name);
                    sendResource(msgChannel, name);
                }
            }
        }
    }

    public HashMap<String, List<File>> jarMap = new HashMap<>();

    public ClassLoadingCodeRunnerClient() {
        if (getClass().getClassLoader() instanceof URLClassLoader) {
            URLClassLoader cl = (URLClassLoader) getClass().getClassLoader();
            for (URL url : cl.getURLs()) {
                URI uri;
                try {
                    uri = url.toURI();
                    if (uri.getScheme().equals("file")) {
                        File file = new File(uri);
                        if (file.exists() && !file.isDirectory()) {
                            try (JarFile jarFile = new JarFile(file)) {
                                Enumeration<JarEntry> entries = jarFile
                                        .entries();
                                while (entries.hasMoreElements()) {
                                    JarEntry entry = entries.nextElement();
                                    if (entry.isDirectory() || entry.getName()
                                            .equals(JarFile.MANIFEST_NAME)) {
                                        continue;
                                    }
                                    List<File> list = jarMap
                                            .get(entry.getName());
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        jarMap.put(entry.getName(), list);
                                    }
                                    list.add(file);
                                }
                            }
                        }
                    }
                } catch (URISyntaxException | IOException e) {
                    // swallow
                }
            }
        }
    }

    private void sendResource(MessageChannel<TMessage> msgChannel,
            String resourceName) {
        {
            List<File> list = jarMap.get(resourceName);
            if (list != null) {
                ArrayList<byte[]> jars = new ArrayList<>();
                for (File file : list) {
                    try (InputStream is = new FileInputStream(file)) {
                        jars.add(toByteArray(is));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                msgChannel.toServer.add(new SendJarsMessage(jars));
                return;
            }

        }
        ClassLoader classLoader = Thread.currentThread()
                .getContextClassLoader();
        InputStream in = classLoader.getResourceAsStream(resourceName);
        if (in == null) {
            msgChannel.toServer
                    .add(new SendResourceMessage(resourceName, null));
        } else {
            try (InputStream ins = in) {
                msgChannel.toServer.add(
                        new SendResourceMessage(resourceName, toByteArray(in)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[1024];
            while (true) {
                int read = in.read(buffer);
                if (read < 0)
                    break;
                out.write(buffer, 0, read);
            }
        } finally {
            try {
                out.close();
            } catch (IOException e1) {
                // swallow
            }

        }
        return out.toByteArray();
    }

    public ParentClassLoaderSupplier getParentClassLoaderSupplier() {
        return parentClassLoaderSupplier;
    }

    public void setParentClassLoaderSupplier(
            ParentClassLoaderSupplier parentClassLoaderSupplier) {
        this.parentClassLoaderSupplier = parentClassLoaderSupplier;
    }

    public CodeRunnerClient getClient() {
        return client;
    }

    public void setRunnerClient(CodeRunnerClient client) {
        this.client = client;
    }
}
