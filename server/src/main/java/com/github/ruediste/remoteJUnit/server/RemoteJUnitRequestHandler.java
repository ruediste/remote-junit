package com.github.ruediste.remoteJUnit.server;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.common.OutErrCombiningStream;
import com.github.ruediste.remoteJUnit.common.Utils;
import com.github.ruediste.remoteJUnit.common.messages.NotifierInvokedMessage;
import com.github.ruediste.remoteJUnit.common.messages.RemoteJUnitMessage;
import com.github.ruediste.remoteJUnit.common.messages.RemoteJUnitMessageVisitor;
import com.github.ruediste.remoteJUnit.common.messages.RequestClassMessage;
import com.github.ruediste.remoteJUnit.common.messages.RunCompletedMessage;
import com.github.ruediste.remoteJUnit.common.messages.RunTestRequest;
import com.github.ruediste.remoteJUnit.common.messages.SendClassMessage;
import com.github.ruediste.remoteJUnit.common.messages.StoppedByUserMessage;
import com.github.ruediste.remoteJUnit.common.requests.GetToClientMessagesRequest;
import com.github.ruediste.remoteJUnit.common.requests.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.common.requests.RemoteJUnitRequestVisitor;
import com.github.ruediste.remoteJUnit.common.requests.SendToServerMessagesRequest;
import com.github.ruediste.remoteJUnit.common.requests.SendToServerMessagesRequest.Entry;
import com.github.ruediste.remoteJUnit.common.responses.EmptyResponse;
import com.github.ruediste.remoteJUnit.common.responses.FailureResponse;
import com.github.ruediste.remoteJUnit.common.responses.RemoteJUnitResponse;
import com.github.ruediste.remoteJUnit.common.responses.TestRunStartedResponse;
import com.github.ruediste.remoteJUnit.common.responses.ToClientMessagesResponse;

public class RemoteJUnitRequestHandler {
    private static final Logger log = LoggerFactory
            .getLogger(RemoteJUnitRequestHandler.class);

    private AtomicLong nextSessionId = new AtomicLong(1);

    private static class Session {
        SessionClassLoader classLoader;
        BlockingQueue<RemoteJUnitMessage> toServer = new LinkedBlockingQueue<RemoteJUnitMessage>();
        BlockingQueue<RemoteJUnitMessage> toClient = new LinkedBlockingQueue<RemoteJUnitMessage>();
        RunNotifier notifier = new SessionRunNotifier(this);

        public void processToServerMessages(boolean wait) {
            boolean first = true;
            while (true) {
                try {
                    RemoteJUnitMessage msg;
                    if (first && wait) {
                        log.debug("Waiting for toServer message");
                        msg = toServer.take();
                    } else
                        msg = toServer.poll();

                    if (msg == null)
                        return;

                    first = false;

                    log.debug("Processing toServer message "
                            + msg.getClass().getSimpleName());
                    msg.accept(new RemoteJUnitMessageVisitor<Object>() {

                        @Override
                        public Object handle(
                                StoppedByUserMessage stoppedByUserMessage) {
                            notifier.pleaseStop();
                            return null;
                        }
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static class SessionClassLoader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }
        private Session session;

        Map<String, byte[]> classData = new HashMap<String, byte[]>();

        SessionClassLoader(Session session, ClassLoader parent) {
            super(parent);
            this.session = session;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            session.processToServerMessages(false);
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

    private ConcurrentMap<Long, Session> sessions = new ConcurrentHashMap<Long, RemoteJUnitRequestHandler.Session>();
    private Supplier<ClassLoader> parentClassloaderSupplier;
    private ExecutorService executor = Executors.newCachedThreadPool();

    public RemoteJUnitRequestHandler(
            Supplier<ClassLoader> parentClassloaderSupplier) {
        this.parentClassloaderSupplier = parentClassloaderSupplier;

    }

    private static class RunTestRunnable implements Runnable {
        private static final Logger log = LoggerFactory
                .getLogger(RunTestRunnable.class);
        private Session session;
        private RunTestRequest req;

        public RunTestRunnable(Session session, RunTestRequest req) {
            this.session = session;
            this.req = req;
        }

        @Override
        public void run() {
            log.debug("starting execution of " + req.testClassName);
            ClassLoader oldContextClassLoader = Thread.currentThread()
                    .getContextClassLoader();
            Thread.currentThread().setContextClassLoader(session.classLoader);
            try {
                Class<?> testClass;
                testClass = session.classLoader.loadClass(req.testClassName);
                final Runner runner = Utils.createRunner(req.runner, testClass);

                Description description = req.description
                        .get(session.classLoader);

                HashSet<Description> allDescriptions = new HashSet<>();
                fillDescriptions(description, allDescriptions);

                Utils.filter(runner, new Filter() {

                    @Override
                    public boolean shouldRun(Description desc) {
                        return allDescriptions.contains(desc);
                    }

                    @Override
                    public String describe() {
                        return "Filter letting pass "
                                + description.getChildren();
                    }
                });

                Utils.sort(runner, new Sorter(new Comparator<Description>() {

                    @Override
                    public int compare(Description o1, Description o2) {
                        int idx1 = 0;
                        for (Description child : description.getChildren()) {
                            if (o1.equals(child))
                                break;
                            idx1++;
                        }
                        int idx2 = 0;
                        for (Description child : description.getChildren()) {
                            if (o2.equals(child))
                                break;
                            idx2++;
                        }
                        return Integer.compare(idx1, idx2);
                    }
                }));

                catchOutput(new Task() {
                    @Override
                    public void run() {
                        runner.run(session.notifier);
                    }
                });
                session.toClient.add(new RunCompletedMessage(null));
            } catch (Exception e) {
                session.toClient.add(new RunCompletedMessage(e));
            } finally {
                Thread.currentThread().setContextClassLoader(
                        oldContextClassLoader);
                log.debug("finished execution of " + req.testClassName);
            }
        }

        private void fillDescriptions(Description description,
                HashSet<Description> allDescriptions) {
            if (allDescriptions.add(description)) {
                description.getChildren().forEach(
                        d -> fillDescriptions(d, allDescriptions));
            }
        }

    }

    public RemoteJUnitResponse handle(RemoteJUnitRequest request) {
        log.debug("handling " + request.getClass().getSimpleName());
        try {
            return request
                    .accept(new RemoteJUnitRequestVisitor<RemoteJUnitResponse>() {

                        @Override
                        public RemoteJUnitResponse handle(RunTestRequest req) {
                            long sessionId = nextSessionId.incrementAndGet();
                            Session session = new Session();
                            sessions.put(sessionId, session);

                            session.classLoader = new SessionClassLoader(
                                    session, parentClassloaderSupplier.get());

                            executor.submit(new RunTestRunnable(session, req));
                            return new TestRunStartedResponse(sessionId);
                        }

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

    private static class SessionRunNotifier extends RunNotifier {
        private Session session;

        SessionRunNotifier(Session session) {
            this.session = session;
        }

        private <T> void sendMessage(String methodName, Class<T> argType, T arg) {
            session.toClient.add(new NotifierInvokedMessage(methodName, argType
                    .getName(), arg));
        }

        @Override
        public void fireTestRunStarted(Description description) {
            sendMessage("fireTestRunStarted", Description.class, description);
            super.fireTestRunStarted(description);
        }

        @Override
        public void fireTestRunFinished(Result result) {
            sendMessage("fireTestRunFinished", Result.class, result);
            super.fireTestRunFinished(result);
        }

        @Override
        public void fireTestStarted(Description description)
                throws StoppedByUserException {
            sendMessage("fireTestStarted", Description.class, description);
            super.fireTestStarted(description);
        }

        @Override
        public void fireTestFailure(Failure failure) {
            sendMessage("fireTestFailure", Failure.class, failure);
            super.fireTestFailure(failure);
        }

        @Override
        public void fireTestAssumptionFailed(Failure failure) {
            sendMessage("fireTestAssumptionFailed", Failure.class, failure);
            super.fireTestAssumptionFailed(failure);
        }

        @Override
        public void fireTestIgnored(Description description) {
            sendMessage("fireTestIgnored", Description.class, description);
            super.fireTestIgnored(description);
        }

        @Override
        public void fireTestFinished(Description description) {
            sendMessage("fireTestFinished", Description.class, description);
            super.fireTestFinished(description);
        }

    }

    private interface Task {
        public void run() throws Exception;
    }

    private static OutErrCombiningStream catchOutput(Task task)
            throws Exception {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        OutErrCombiningStream combination = new OutErrCombiningStream();
        RedirectingStream out = new RedirectingStream(origOut,
                combination.getOut());
        RedirectingStream err = new RedirectingStream(origErr,
                combination.getErr());

        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        try {
            task.run();
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
        combination.commitLastEntry();
        return combination;
    }
}
