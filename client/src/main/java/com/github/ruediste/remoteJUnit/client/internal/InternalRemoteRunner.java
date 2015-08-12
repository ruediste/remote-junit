package com.github.ruediste.remoteJUnit.client.internal;

import junit.framework.AssertionFailedError;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerScheduler;
import org.junit.runners.model.TestClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.remoteJUnit.server.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class InternalRemoteRunner
		extends BlockJUnit4ClassRunner {

	private static final Logger log = LoggerFactory.getLogger(InternalRemoteRunner.class);

	private static List<String> endpoints = new ArrayList<String>();
	private static int currentEndpoint = 0;
	private Description description;
	private Map<Description, String> methodNames = new HashMap<Description, String>();
	private final Class<?> testClass;
	private Class<? extends Runner> remoteRunnerClass;
	private static ExecutorService executorService;
	private static final ReducibleSemaphore SEMAPHORE = new ReducibleSemaphore();

	public InternalRemoteRunner(Class<?> testClass, String endpoint, Class<? extends Runner> remoteRunnerClass)
			throws InitializationError {
		super(testClass);
		this.testClass = testClass;
		this.remoteRunnerClass = remoteRunnerClass;
		TestClass tc = new TestClass(testClass);

		description = Description.createTestDescription(testClass, tc.getName(), tc.getAnnotations());

		for (FrameworkMethod method : tc.getAnnotatedMethods(Test.class)) {
			String methodName = method.getName();
			Description child = Description.createTestDescription(testClass, methodName, method.getAnnotations());

			methodNames.put(child, methodName);
			description.addChild(child);
		}

		if (executorService == null) {
			String ep = System.getProperty("junit.remote.endpoint");
			if (ep == null) {
				ep = endpoint;
			}

			for (String e : ep.split(",")) {
				if (e.trim().equals("")) { continue; }
				endpoints.add(e.trim());
			}
			executorService = Executors.newFixedThreadPool(endpoints.size());
		}

		setScheduler(
				new RunnerScheduler() {
					@Override
					public void schedule(final Runnable childStatement) {
						SEMAPHORE.reducePermits(1);
						executorService.submit(new SemaphoreDelegate(childStatement));
					}

					@Override
					public void finished() {
						try {
							SEMAPHORE.acquire();
							SEMAPHORE.release();
						} catch (InterruptedException ignore) {
							Thread.currentThread().interrupt();
						}
					}
				});
	}

	@Override
	public void filter(Filter filter)
			throws NoTestsRemainException {
		super.filter(filter);
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
	protected void runChild(FrameworkMethod method, RunNotifier notifier) {
		Description description = describeChild(method);
		if (method.getAnnotation(Ignore.class) != null) {
			notifier.fireTestIgnored(description);
			return;
		}

		String methodName = method.getName();
		String allowedMethodName = methodNames.get(description);
		if (methodName != null && !methodName.equals(allowedMethodName)) {
//			notifier.fireTestIgnored(description);
			return;
		}

		try {
			notifier.fireTestStarted(description);
			HttpURLConnection connection = getUrl(methodName, "POST");
			handleError(connection);

			String enc = connection.getContentEncoding();
			if (enc == null) { enc = "ISO-8859-1"; }
			InputStream is = connection.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName(enc)));
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("E")) {
					System.err.println(line.substring(1).trim());
				} else if (line.startsWith("O")) {
					System.out.println(line.substring(1).trim());
				} else if (line.startsWith("RSUCCESS")) {
					break;
				} else if (line.startsWith("RERROR")) {
					StringBuilder error = new StringBuilder(line.substring(6));
					while ((line = reader.readLine()) != null) {
						error.append(line).append("\n");
					}
					throw new AssertionFailedError(error.toString());
				} else {
					log.error("Protocol error in response: {}", line);
				}
			}
			is.close();
			connection.disconnect();
		} catch (Throwable e) {
			e.printStackTrace();
			notifier.fireTestFailure(new Failure(description, e));
		} finally {
			notifier.fireTestFinished(description);
		}

	}

	private void handleError(HttpURLConnection connection)
			throws IOException {
		if (connection.getResponseCode() != 200) {
			String error = null;
			InputStream err = connection.getErrorStream();
			if (err != null) {
				error = Utils.toString(err);
			}
			if (error == null) {
				error = connection.getResponseMessage();
			}
			throw new RuntimeException("Unable to send request to " + connection.getURL() + ": " + error);
		}
	}

	private HttpURLConnection getUrl(String methodName, String httpMethod) {
		int count = 0;
		while (count < endpoints.size() * 2) {
			String ep = endpoints.get(currentEndpoint++ % endpoints.size());
			if (!ep.endsWith("/")) {
				ep = ep + "/";
			}
			try {

				HttpURLConnection connection = (HttpURLConnection) new URL(
						ep + testClass.getName() + "?method=" + methodName + "&runner=" +
								remoteRunnerClass.getName()).openConnection();
				connection.setReadTimeout(120000);
				connection.setAllowUserInteraction(false);
				connection.setUseCaches(false);
				connection.setRequestMethod(httpMethod);
				connection.setRequestProperty("Connection", "close");
				connection.connect();

				return connection;
			} catch (MalformedURLException e) {
				throw new RuntimeException("Unable to create remote url", e);
			} catch (ConnectException e) {
				log.warn("Skipping host {}", ep);
				count++;
			} catch (IOException e) {
				throw new RuntimeException("Unable to connect", e);
			}
		}
		throw new RuntimeException("No hosts available");
	}

	private static class SemaphoreDelegate
			implements Runnable {
		private final Runnable childStatement;

		SemaphoreDelegate(Runnable childStatement) {this.childStatement = childStatement;}

		@Override
		public void run() {
			try {
				childStatement.run();
			} finally {
				SEMAPHORE.release();
			}
		}
	}

	private static class ReducibleSemaphore
			extends Semaphore {
		private static final long serialVersionUID = 1L;

		public ReducibleSemaphore() {super(1);}

		@Override
		public void reducePermits(int reduction) {
			super.reducePermits(reduction);
		}
	}
}

