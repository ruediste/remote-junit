package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon.FailureResponse;

public class ClassLoadingRemoteCode implements CodeRunnerCommon.Code {

    private interface RemoteJUnitMessage extends Serializable {
        <T> T accept(RemoteJUnitMessageVisitor<T> visitor);
    }

    public static class RequestClassMessage implements RemoteJUnitMessage {

        private static final long serialVersionUID = 1L;
        public String name;

        public RequestClassMessage(String name) {
            this.name = name;
        }

        @Override
        public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
            return visitor.handle(this);
        }

    }

    public static class RunCompletedMessage implements RemoteJUnitMessage {
        private static final long serialVersionUID = 1L;
        public Throwable failure;

        public RunCompletedMessage(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
            return visitor.handle(this);
        }

    }

    public static class SendClassMessage implements RemoteJUnitMessage {
        private static final long serialVersionUID = 1L;
        public byte[] data;
        public String name;

        public SendClassMessage(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        @Override
        public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
            return visitor.handle(this);
        }

    }

    public static class CustomMessageWrapper implements RemoteJUnitMessage {
        private static final long serialVersionUID = 1L;

        @Override
        public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
            return visitor.handle(this);
        }

    }

    public static class RemoteJUnitMessageVisitor<T> {

        public T unHandled(RemoteJUnitMessage message) {
            throw new UnsupportedOperationException();
        }

        public T handle(CustomMessageWrapper customMessageWrapper) {
            return unHandled(customMessageWrapper);
        }

        public T handle(RequestClassMessage sendClassMessage) {
            return unHandled(sendClassMessage);
        }

        public T handle(RunCompletedMessage runCompletedMessage) {
            return unHandled(runCompletedMessage);
        }

        public T handle(SendClassMessage sendClassMessage) {
            return unHandled(sendClassMessage);
        }

    }

    public interface RemoteJUnitResponse extends Serializable {

    }

    public class EmptyResponse implements RemoteJUnitResponse {

        private static final long serialVersionUID = 1L;

    }

    public static class ToClientMessagesResponse implements RemoteJUnitResponse {
        private static final long serialVersionUID = 1L;
        public List<RemoteJUnitMessage> messages;

        public ToClientMessagesResponse(List<RemoteJUnitMessage> messages) {
            this.messages = messages;
        }

    }

    public interface RemoteJUnitRequest extends Serializable {
    }

    public class GetToClientMessagesRequest implements RemoteJUnitRequest {
        private static final long serialVersionUID = 1L;
        public long sessionId;

        public GetToClientMessagesRequest(long sessionId) {
            this.sessionId = sessionId;
        }

    }

    public class SendToServerMessagesRequest implements RemoteJUnitRequest {
        private static final long serialVersionUID = 1L;

        public List<RemoteJUnitMessage> messages = new ArrayList<>();
        public long sessionId;

        public SendToServerMessagesRequest(List<RemoteJUnitMessage> messages,
                long sessionId) {
            this.sessionId = sessionId;
            this.messages = messages;
        }

    }

    private static class SessionClassLoader extends ClassLoader {
        private static final Logger log = LoggerFactory
                .getLogger(SessionClassLoader.class);
        static {
            registerAsParallelCapable();
        }
        private ClassLoadingRemoteCode session;

        Map<String, byte[]> classData = new HashMap<String, byte[]>();

        SessionClassLoader(ClassLoadingRemoteCode session, ClassLoader parent) {
            super(parent);
            this.session = session;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] data;
            synchronized (classData) {
                data = classData.get(name);
            }
            if (data == null) {
                log.debug("requesting from client: " + name);
                // request class
                try {
                    session.toClient.put(new RequestClassMessage(name));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // wait for class to be returned
                do {
                    synchronized (classData) {
                        data = classData.get(name);
                        if (data == null)
                            try {
                                classData.wait();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                    }
                } while (data == null);
                log.debug("loaded from client: " + name);
            }
            if (data.length == 0)
                throw new ClassNotFoundException(name);
            else
                return defineClass(name, data, 0, data.length);
        }

        public void addClass(SendClassMessage msg) {
            synchronized (classData) {
                classData.put(msg.name, msg.data);
                classData.notifyAll();
            }
        }
    }

    SessionClassLoader classLoader;
    BlockingQueue<RemoteJUnitMessage> toServer = new LinkedBlockingQueue<RemoteJUnitMessage>();
    BlockingQueue<RemoteJUnitMessage> toClient = new LinkedBlockingQueue<RemoteJUnitMessage>();

    protected ClassLoader getParentClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public void run() {
        classLoader = new SessionClassLoader(this, getParentClassLoader());
    }

    @Override
    public byte[] handle(byte[] request) {
        log.debug("handling " + request.getClass().getSimpleName());
        try {
            return request
                    .accept(new RemoteJUnitRequestVisitor<RemoteJUnitResponse>() {

                        @Override
                        public RemoteJUnitResponse handle(
                                GetToClientMessagesRequest getToClientMessagesRequest) {
                            Session session = sessions
                                    .get(getToClientMessagesRequest.sessionId);
                            List<RemoteJUnitMessage> messages = new ArrayList<>();
                            if (session != null) {
                                try {
                                    if (true)
                                        messages.add(session.toClient.take());
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                session.toClient.drainTo(messages);
                            }
                            return new ToClientMessagesResponse(messages);
                        }

                        @Override
                        public RemoteJUnitResponse handle(
                                SendToServerMessagesRequest sendToServerMessagesRequest) {
                            Session session = sessions
                                    .get(sendToServerMessagesRequest.sessionId);
                            if (session != null) {
                                List<RemoteJUnitMessage> msgs = new ArrayList<>();
                                for (Entry entry : sendToServerMessagesRequest.messages) {
                                    if (SendClassMessage.class
                                            .equals(entry.cls)) {
                                        session.classLoader
                                                .addClass((SendClassMessage) entry.message
                                                        .get());
                                    } else
                                        msgs.add(entry.message
                                                .get(session.classLoader));
                                }
                                session.toServer.addAll(msgs);
                            } else
                                log.warn("Session "
                                        + sendToServerMessagesRequest.sessionId
                                        + " not found");
                            return new EmptyResponse();
                        }
                    });
        } catch (Exception e) {
            return new FailureResponse(e);
        } finally {
            log.debug("handling " + request.getClass().getSimpleName()
                    + " done");
        }
    }

}
