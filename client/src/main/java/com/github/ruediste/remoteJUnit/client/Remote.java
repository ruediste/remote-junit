package com.github.ruediste.remoteJUnit.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;

/**
 * Provide additional meta data for tests run with the {@link RemoteTestRunner}.
 * 
 * Use this on either a class itself or any of it's superclasses.
 * 
 * The annotation will only be picked up if the class is also {@link RunWith}(
 * {@link RemoteTestRunner}.class)
 * 
 * @author recht
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Remote {

    /**
     * The endpoint to use for test execution. This should point to an instance
     * of the RemoteServer, and should not include any path.
     * 
     * @return
     */
    String endpoint() default "http://localhost:4578/";

    /**
     * The remote runner class. Can be any runner, as long as it's on classpath.
     * For example, it should be the SpringJUnit4ClassRunner
     */
    Class<? extends Runner> runnerClass() default BlockJUnit4ClassRunner.class;

}
