package com.github.ruediste.remoteJUnit.client.internal;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
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

import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient;
import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingRemoteCodeRunnerClient.RemoteCodeEnvironment;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient;
import com.github.ruediste.remoteJUnit.codeRunner.CodeRunnerClient.ClassMapBuilder;
import com.github.ruediste.remoteJUnit.codeRunner.ParentClassLoaderSupplier;

public class InternalRemoteRunner extends Runner
        implements Filterable, Sortable {
    private static final Logger log = LoggerFactory
            .getLogger(InternalRemoteRunner.class);

    public interface RemoteJUnitMessage extends Serializable {

        <T> T accept(RemoteJUnitMessageVisitor<T> visitor);
    }

    public static class NotifierInvokedMessage implements RemoteJUnitMessage {

        private static final long serialVersionUID = 1L;
        public String methodName;
        public String argTypeClass;
        public Object arg;

        public NotifierInvokedMessage(String methodName, String argTypeClass,
                Object arg) {
            this.methodName = methodName;
            this.argTypeClass = argTypeClass;
            this.arg = arg;
        }

        @Override
        public <T> T accept(RemoteJUnitMessageVisitor<T> visitor) {
            return visitor.handle(this);
        }

        @Override
        public String toString() {
            return "NotifierInvokedMessage(" + methodName + "(" + arg + "))";
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

        @Override
        public String toString() {
            return "RunCompletedMessage(" + failure + ")";
        }
    }

    public static class StoppedByUserMessage implements RemoteJUnitMessage {

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

        public T handle(NotifierInvokedMessage notifierInvokedMessage) {
            return unHandled(notifierInvokedMessage);
        }

        public T handle(RunCompletedMessage runCompletedMessage) {
            return unHandled(runCompletedMessage);
        }

        public T handle(StoppedByUserMessage stoppedByUserMessage) {
            return unHandled(stoppedByUserMessage);
        }

    }

    private static class ServerCode implements
            Consumer<RemoteCodeEnvironment<RemoteJUnitMessage>>, Serializable {
        private static final long serialVersionUID = 1L;

        private static class SessionRunNotifier extends RunNotifier {
            private Consumer<RemoteJUnitMessage> toClientSender;

            SessionRunNotifier(Consumer<RemoteJUnitMessage> toClientSender) {
                this.toClientSender = toClientSender;
            }

            private <T> void sendMessage(String methodName, Class<T> argType,
                    T arg) {
                log.debug("send notifier invocation: {}({})", methodName, arg);
                toClientSender.accept(new NotifierInvokedMessage(methodName,
                        argType.getName(), arg));
            }

            @Override
            public void fireTestRunStarted(Description description) {
                sendMessage("fireTestRunStarted", Description.class,
                        description);
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

        Class<?> testClass;
        Class<? extends Runner> runnerClass;
        Description description;
        private volatile boolean completed;

        public ServerCode(Class<?> testClass,
                Class<? extends Runner> runnerClass, Description description) {
            super();
            this.testClass = testClass;
            this.runnerClass = runnerClass;
            this.description = description;
        }

        @Override
        public void accept(RemoteCodeEnvironment<RemoteJUnitMessage> env) {
            log.debug("starting execution of " + testClass.getName());

            try {

                RunNotifier notifier = new SessionRunNotifier(
                        env::sendToClient);
                // start toServer message handler
                {
                    Thread t = new Thread(() -> {
                        while (!completed) {
                            RemoteJUnitMessage msg;
                            try {
                                msg = env.getToServerMessages().poll(1,
                                        TimeUnit.SECONDS);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            if (msg != null) {
                                log.debug("handling toServer message " + msg);
                                if (msg instanceof StoppedByUserMessage) {
                                    log.debug("invoking notifier.pleaseStop()");
                                    notifier.pleaseStop();
                                } else
                                    throw new RuntimeException(
                                            "Unknown message " + msg);
                            }
                        }
                    });
                    t.setName("toServer message handler");
                    t.setDaemon(true);
                    t.start();
                }
                final Runner runner = Utils.createRunner(runnerClass,
                        testClass);

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

                runner.run(notifier);
                env.sendToClient(new RunCompletedMessage(null));
            } catch (Throwable t) {
                env.sendToClient(new RunCompletedMessage(t));
            } finally {
                completed = true;
                log.debug("finished execution of " + testClass.getName());
            }
        }

        private void fillDescriptions(Description description,
                HashSet<Description> allDescriptions) {
            if (allDescriptions.add(description)) {
                description.getChildren()
                        .forEach(d -> fillDescriptions(d, allDescriptions));
            }
        }
    }

    private final class ToClientMessageHandler
            extends RemoteJUnitMessageVisitor<Boolean> {
        private RunNotifier notifier;
        private Consumer<RemoteJUnitMessage> toServerSender;

        public ToClientMessageHandler(
                Consumer<RemoteJUnitMessage> toServerSender,
                RunNotifier notifier) {
            this.toServerSender = toServerSender;
            this.notifier = notifier;
        }

        @Override
        public Boolean handle(RunCompletedMessage runCompletedMessage) {
            Throwable failure = runCompletedMessage.failure;
            if (failure != null) {
                if (failure instanceof RuntimeException)
                    throw (RuntimeException) failure;
                if (failure instanceof Error)
                    throw (Error) failure;
                throw new RuntimeException("Error in remote unit test",
                        failure);
            }
            return true;
        }

        @Override
        public Boolean handle(NotifierInvokedMessage msg) {
            Object arg = msg.arg;
            log.debug("invoking method " + msg.methodName
                    + "() on notifier with argument " + arg);
            Class<?> argType;
            try {
                argType = RunNotifier.class.getClassLoader()
                        .loadClass(msg.argTypeClass);
                Method method = RunNotifier.class.getMethod(msg.methodName,
                        argType);
                method.invoke(notifier, arg);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof StoppedByUserException) {
                    if (pleaseStopSent.compareAndSet(false, true)) {
                        log.debug("sending StoppedByUserMessage");
                        toServerSender.accept(new StoppedByUserMessage());
                    }
                } else
                    throw new RuntimeException(e.getCause());
            } catch (Exception e) {
                log.warn("Error while invoking RunNotifier method", e);
            }
            return false;
        }
    }

    private Description description;

    private Map<Description, String> methodNames = new HashMap<Description, String>();
    private final Class<?> testClass;
    private Class<? extends Runner> remoteRunnerClass;

    private String endpoint;

    private AtomicBoolean pleaseStopSent = new AtomicBoolean(false);
    private Class<? extends ParentClassLoaderSupplier> parentClassloaderSupplierClass;

    public InternalRemoteRunner(Class<?> testClass, String endpoint,
            Class<? extends Runner> remoteRunnerClass,
            Class<? extends ParentClassLoaderSupplier> parentClassloaderSupplierClass)
                    throws InitializationError {
        this.testClass = testClass;
        this.endpoint = endpoint;
        this.remoteRunnerClass = remoteRunnerClass;
        this.parentClassloaderSupplierClass = parentClassloaderSupplierClass;
        TestClass tc = new TestClass(testClass);

        description = Description.createSuiteDescription(testClass);

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
        try {
            log.debug("starting remote execution of " + testClass);
            // try {
            ClassLoadingRemoteCodeRunnerClient<RemoteJUnitMessage> client = new ClassLoadingRemoteCodeRunnerClient<>();
            client.setRunnerClient(new CodeRunnerClient(endpoint));
            if (parentClassloaderSupplierClass != null) {
                ParentClassLoaderSupplier parentClassLoaderSupplier;
                try {
                    parentClassLoaderSupplier = parentClassloaderSupplierClass
                            .newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                client.setParentClassLoaderSupplier(parentClassLoaderSupplier);
            }
            client.runCode(
                    new ServerCode(testClass, remoteRunnerClass, description),
                    (msg, sender) -> {
                        log.debug("handling toClient message " + msg);
                        msg.accept(
                                new ToClientMessageHandler(sender, notifier));
                    } ,
                    new ClassMapBuilder().addClass(InternalRemoteRunner.class,
                            RunCompletedMessage.class));
        } catch (Exception e) {
            notifier.fireTestFailure(new Failure(description, e));
        }

    }

}
