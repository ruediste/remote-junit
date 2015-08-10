package com.github.ruediste.remoteJUnit.server;

import java.io.PrintStream;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import com.github.ruediste.remoteJUnit.common.OutErrCombiningStream;
import com.github.ruediste.remoteJUnit.messages.RemoteJUnitRequest;
import com.github.ruediste.remoteJUnit.messages.RemoteJUnitRequestVisitor;
import com.github.ruediste.remoteJUnit.messages.RemoteJUnitResponse;
import com.github.ruediste.remoteJUnit.messages.RunTestRequest;

public class RemoteJUnitRequestHandler {

    public RemoteJUnitResponse handle(RemoteJUnitRequest request) {

        request.accept(new RemoteJUnitRequestVisitor() {
            @Override
            public void handle(RunTestRequest req) {
                Class<?> testClass;
                try {
                    testClass = Class.forName(req.testClassName);
                    final Runner runner = Utils.createRunner(req.runner,
                            testClass);
                    if (req.method != null) {
                        try {
                            Utils.filter(runner, Filter
                                    .matchMethodDescription(Description
                                            .createTestDescription(testClass,
                                                    req.method)));
                        } catch (NoTestsRemainException e) {
                            pw.println("RERRORNo tests remaining");
                            return;
                        }
                    }
                    try {
                        MyRunListener listener = new MyRunListener();
                        final RunNotifier notifier = new RunNotifier();
                        notifier.addListener(listener);
                        catchOutput(new Task() {
                            @Override
                            public void run() {
                                runner.run(notifier);
                            }
                        });
                        pw.println(listener.getResult());
                    } catch (Exception e1) {
                        pw.println("RERROR" + e1);
                    }
                } catch (ClassNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });
        RemoteJUnitResponse result = new RemoteJUnitResponse();
        result.response = "Hello from the server";
        return result;
    }

    private final class MyRunListener extends RunListener {

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
