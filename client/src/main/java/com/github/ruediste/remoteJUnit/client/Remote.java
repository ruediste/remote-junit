package com.github.ruediste.remoteJUnit.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;

import com.github.ruediste.remoteJUnit.codeRunner.ParentClassLoaderSupplier;

/**
 * Provide additional meta data for tests run with the {@link RemoteTestRunner}.
 * 
 * <p>
 * Use this on either a class itself or any of it's superclasses.
 * 
 * <p>
 * The annotation will only be picked up if the class is also {@link RunWith}(
 * {@link RemoteTestRunner}.class)
 * 
 * <p>
 * If a subclass is annotated, all non-default elements of this annotation will
 * override elements specified on the parent class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Remote {

    /**
     * The endpoint to use for test execution. Default is
     * 'http://localhost:4578/'. If set to '-', the test is executed locally.
     */
    String endpoint() default "";

    /**
     * The remote runner class. Can be any runner, as long as it's on classpath.
     * Default is {@link BlockJUnit4ClassRunner}
     */
    Class<? extends Runner>runnerClass() default Runner.class;

    /**
     * Used on the server to obtain the parent of the class loader used to load
     * the unit tests.
     */
    Class<? extends ParentClassLoaderSupplier>parentClassloaderSupplier() default ParentClassLoaderSupplier.class;

    /**
     * If set to false, tests are NOT executed locally if the connection to the
     * remote server cannot be established. Default is true. The array is
     * intended to be empty contain or a single boolean.
     */
    boolean[]allowLocalExecution() default {};
}
