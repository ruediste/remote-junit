package com.github.ruediste.remoteJUnit.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class RedirectingStream extends OutputStream {

    private final PrintStream delegate;
    private OutputStream redirector;

    public RedirectingStream(PrintStream delegate, OutputStream redirector) {
        this.delegate = delegate;
        this.redirector = redirector;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
        if (redirector != null) {
            redirector.write(b);
        }
    }

}
