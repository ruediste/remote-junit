package com.github.ruediste.remoteJUnit.client.internal;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

public class InternalRemoteRunner extends Runner implements Filterable,
        Sortable {
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

    private static final Logger log = LoggerFactory
            .getLogger(InternalRemoteRunner.class);

    private static class SessionRunNotifier extends RunNotifier {
        private Consumer<RemoteJUnitMessage> toClientSender;

        SessionRunNotifier(Consumer<RemoteJUnitMessage> toClientSender) {
            this.toClientSender = toClientSender;
        }

        private <T> void sendMessage(String methodName, Class<T> argType, T arg) {
            toClientSender.accept(new NotifierInvokedMessage(methodName,
                    argType.getName(), arg));
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

    private static class ServerCode implements
            Consumer<RemoteCodeEnvironment<RemoteJUnitMessage>>, Serializable {
        private static final long serialVersionUID = 1L;
        Class<?> testClass;
        Class<? extends Runner> runnerClass;
        Description description;

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
            ClassLoader oldContextClassLoader = Thread.currentThread()
                    .getContextClassLoader();
            Thread.currentThread().setContextClassLoader(
                    getClass().getClassLoader());
            try {

                final Runner runner = Utils
                        .createRunner(runnerClass, testClass);

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

                RunNotifier notifier = new SessionRunNotifier(env::sendToClient);
                runner.run(notifier);
                env.sendToClient(new RunCompletedMessage(null));
            } catch (Exception e) {
                env.sendToClient(new RunCompletedMessage(e));
            } finally {
                Thread.currentThread().setContextClassLoader(
                        oldContextClassLoader);
                log.debug("finished execution of " + testClass.getName());
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

    private final class ToClientMessageHandler extends
            RemoteJUnitMessageVisitor<Boolean> {
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
                toServerSender.accept(new StoppedByUserMessage());
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

        try {

            ClassLoadingRemoteCodeRunnerClient<RemoteJUnitMessage> client = new ClassLoadingRemoteCodeRunnerClient<>();
            client.setRunnerClient(new CodeRunnerClient(endpoint));
            client.runCode(new ServerCode(testClass, remoteRunnerClass,
                    description), (msg, sender) -> {
                msg.accept(new ToClientMessageHandler(sender, notifier));
            });
        } catch (Exception e) {
            notifier.fireTestFailure(new Failure(description, e));
            return;
        }

    }

}
