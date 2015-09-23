package com.github.ruediste.remoteJUnit.codeRunner;

import java.io.Serializable;

import com.github.ruediste.remoteJUnit.codeRunner.ClassLoadingCodeRunnerClient.MessageHandlingEnvironment;

/**
 * Code executed by the {@link ClassLoadingCodeRunnerClient} on the server
 */
public interface MessageHandlingServerCode<TMessage> extends Serializable {

    /**
     * Run the code
     */
    public void run(MessageHandlingEnvironment<TMessage> env);
}
