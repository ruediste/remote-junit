package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.Serializable;

/**
 * Code to be executed on the server
 */
public interface RequestHandlingServerCode extends Runnable, Serializable {

    /**
     * Called after deserializing the code on the server, before calling
     * {@link #run()}.
     * 
     * <p>
     * The response to the start request is sent as soon as this method returns.
     * It is therefore guaranteed that {@link #handle(byte[])} is not invoked
     * before this method returns.
     */
    void initialize();

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