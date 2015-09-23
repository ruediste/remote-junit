package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.Serializable;

/**
 * Code to be executed on the server
 */
public interface ServerCode extends Runnable, Serializable {
    /**
     * Called in the remote VM. The code can receive requests while this method
     * is running.
     */
    @Override
    void run();

    /**
     * Handle a custom request, while {@link #run()} is running
     */
    byte[] handle(byte[] request);
}