package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.Serializable;

/**
 * Used to obtain the parent class loader on the server when using the
 * {@link ClassLoadingCodeRunnerClient}. Subclasses must provide a no args
 * constructor.
 */
@FunctionalInterface
public interface ParentClassLoaderSupplier extends Serializable {
    ClassLoader getParentClassLoader();
}
