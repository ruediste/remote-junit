package com.github.ruediste.remoteJUnit.client.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerCommon;
import com.github.ruediste.remoteJUnit.common.messages.NotifierInvokedMessage;
import com.github.ruediste.remoteJUnit.common.messages.NullMessage;
import com.github.ruediste.remoteJUnit.common.messages.RemoteJUnitMessage;
import com.github.ruediste.remoteJUnit.common.messages.RemoteJUnitMessageVisitor;
import com.github.ruediste.remoteJUnit.common.messages.RequestClassMessage;
import com.github.ruediste.remoteJUnit.common.messages.RunCompletedMessage;
import com.github.ruediste.remoteJUnit.common.messages.RunTestRequest;
import com.github.ruediste.remoteJUnit.common.messages.SendClassMessage;
import com.github.ruediste.remoteJUnit.common.messages.StoppedByUserMessage;
import com.github.ruediste.remoteJUnit.common.requests.GetToClientMessagesRequest;
import com.github.ruediste.remoteJUnit.common.requests.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.common.requests.SendToServerMessagesRequest;
import com.github.ruediste.remoteJUnit.common.responses.FailureResponse;
import com.github.ruediste.remoteJUnit.common.responses.RemoteJUnitResponse;
import com.github.ruediste.remoteJUnit.common.responses.TestRunStartedResponse;
import com.github.ruediste.remoteJUnit.common.responses.ToClientMessagesResponse;

public class InternalRemoteRunner extends Runner implements Filterable,
        Sortable {

    private final class ServerCode implements CodeRunnerCommon.Code {

        @Override
        public void run() {
            // TODO Auto-generated method stub

        }

        @Override
        public byte[] handle(byte[] request) {
            // TODO Auto-generated method stub
            return null;
        }

    }

    private final class ToClientMessageHandler extends
            RemoteJUnitMessageVisitor<Boolean> {
        private BlockingQueue<RemoteJUnitMessage> toServer;
        private RunNotifier notifier;

        public ToClientMessageHandler(
                BlockingQueue<RemoteJUnitMessage> toServer, RunNotifier notifier) {
            this.toServer = toServer;
            this.notifier = notifier;
        }

        @Override
        public Boolean handle(RequestClassMessage sendClassMessage) {
            log.debug("handling sendClassMessage for " + sendClassMessage.name);
            InputStream in = Thread
                    .currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(
                            sendClassMessage.name.replace('.', '/') + ".class");
            if (in == null) {
                toServer.add(new SendClassMessage(sendClassMessage.name,
                        new byte[] {}));
                return false;
            }
            ByteArrayOutputStream out = null;
            try {
                out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                while (true) {
                    int read = in.read(buffer);
                    if (read < 0)
                        break;
                    out.write(buffer, 0, read);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (out != null)
                    try {
                        out.close();
                    } catch (IOException e1) {
                        // swallow
                    }
                if (in != null)
                    try {
                        in.close();
                    } catch (IOException e) {
                        // swallow
                    }
            }
            toServer.add(new SendClassMessage(sendClassMessage.name, out
                    .toByteArray()));
            return false;
        }

        @Override
        public Boolean handle(RunCompletedMessage runCompletedMessage) {
            if (runCompletedMessage.failure != null)
                throw new RuntimeException("Error in remote unit test",
                        runCompletedMessage.failure);
            return true;
        }

        @Override
        public Boolean handle(NotifierInvokedMessage msg) {
            Class<?> argType;
            try {
                argType = RunNotifier.class.getClassLoader().loadClass(
                        msg.argTypeClass);
                Method method = RunNotifier.class.getMethod(msg.methodName,
                        argType);
                method.invoke(notifier, msg.arg);
            } catch (StoppedByUserException e) {
                toServer.add(new StoppedByUserMessage());
            } catch (Exception e) {
                log.warn("Error while invoking RunNotifier method", e);
            }
            return false;
        }
    }

    private final class ToServerSender implements Runnable {
        public volatile boolean finish;
        private BlockingQueue<RemoteJUnitMessage> toServer;
        private long sessionId;

        public ToServerSender(BlockingQueue<RemoteJUnitMessage> toServer,
                long sessionId) {
            this.toServer = toServer;
            this.sessionId = sessionId;
        }

        @Override
        public void run() {
            while (!finish) {
                try {
                    List<RemoteJUnitMessage> messages = new ArrayList<>();
                    messages.add(toServer.take());
                    if (finish)
                        return;
                    toServer.drainTo(messages);
                    sendMessage(new SendToServerMessagesRequest(messages,
                            sessionId));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static final Logger log = LoggerFactory
            .getLogger(InternalRemoteRunner.class);

    private Description description;
    private Map<Description, String> methodNames = new HashMap<Description, String>();
    private final Class<?> testClass;
    private Class<? extends Runner> remoteRunnerClass;

    private String endpoint;

    public InternalRemoteRunner(Class<?> testClass, String endpoint,
            Class<? extends Runner> remoteRunnerClass)
            throws InitializationError {
        this.testClass = testClass;
        this.endpoint = endpoint;
        this.remoteRunnerClass = remoteRunnerClass;
        TestClass tc = new TestClass(testClass);

        description = Description.createTestDescription(testClass,
                tc.getName(), tc.getAnnotations());

        for (FrameworkMethod method : tc.getAnnotatedMethods(Test.class)) {
            String methodName = method.getName();
            Description child = Description.createTestDescription(testClass,
                    methodName, method.getAnnotations());

            methodNames.put(child, methodName);
            description.addChild(child);
        }
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        List<Description> children = description.getChildren();

        Iterator<Description> itr = children.iterator();
        while (itr.hasNext()) {
            Description child = itr.next();
            if (!filter.shouldRun(child)) {
                itr.remove();
                methodNames.remove(child);
            }
        }

        if (children.isEmpty()) {
            throw new NoTestsRemainException();
        }
    }

    @Override
    public void sort(Sorter sorter) {
        Collections.sort(description.getChildren(), sorter);
    }

    @Override
    public Description getDescription() {
        return description;
    }

    @Override
    public void run(RunNotifier notifier) {
        notifier.fireTestStarted(description);

        TestRunStartedResponse runStarted;
        try {
            runStarted = (TestRunStartedResponse) sendMessage(new RunTestRequest(
                    remoteRunnerClass.getName(), testClass.getName(),
                    description));
        } catch (Exception e) {
            notifier.fireTestFailure(new Failure(description, e));
            return;
        }

        BlockingQueue<RemoteJUnitMessage> toServer = new LinkedBlockingQueue<>();

        // start client to server thread
        ToServerSender toServerSender = new ToServerSender(toServer,
                runStarted.sessionId);
        new Thread(toServerSender).start();

        // process incoming messages
        try {
            while (true) {
                ToClientMessagesResponse messagesResponse = (ToClientMessagesResponse) sendMessage(new GetToClientMessagesRequest(
                        runStarted.sessionId));
                for (RemoteJUnitMessage message : messagesResponse.messages) {
                    boolean quit = message.accept(new ToClientMessageHandler(
                            toServer, notifier));
                    if (quit)
                        return;

                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } finally {
            toServerSender.finish = true;
            toServer.add(new NullMessage());
        }
    }

    private RemoteJUnitResponse sendMessage(RemoteJUnitRequest request) {
        log.debug("sending " + request.getClass().getSimpleName());
        try {
            URL serverUrl = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) serverUrl
                    .openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/octet-stream");

            ObjectOutputStream os = new ObjectOutputStream(
                    conn.getOutputStream());
            os.writeObject(request);
            os.close();

            ObjectInputStream in = new ObjectInputStream(conn.getInputStream());
            RemoteJUnitResponse resp = (RemoteJUnitResponse) in.readObject();
            in.close();
            if (resp instanceof FailureResponse)
                throw new RuntimeException(((FailureResponse) resp).exception);
            return resp;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            log.debug("sending " + request.getClass().getSimpleName() + " done");
        }
    }
}
