package com.github.ruediste.remoteJUnit.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class OutErrCombiningStream implements Serializable {

    private static final long serialVersionUID = 1L;

    static class Entry {
        boolean isErr = false;
        byte[] data;

        public Entry(boolean isErr, byte[] data) {
            super();
            this.isErr = isErr;
            this.data = data;
        }

    }

    List<Entry> entries = new ArrayList<OutErrCombiningStream.Entry>();

    transient private boolean currentIsErr;
    transient private ByteArrayOutputStream currentBaos = new ByteArrayOutputStream();

    private final OutputStream out = new OutputStream() {

        @Override
        public void write(int b) throws IOException {
            checkEntry();
            currentBaos.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            checkEntry();
            currentBaos.write(b, off, len);
        };

        private void checkEntry() {
            if (currentIsErr) {
                commitLastEntry();
                currentIsErr = false;
            }
        }

    };
    private final OutputStream err = new OutputStream() {

        @Override
        public void write(int b) throws IOException {
            checkEntry();
            currentBaos.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            checkEntry();
            currentBaos.write(b, off, len);
        };

        private void checkEntry() {
            if (!currentIsErr) {
                commitLastEntry();
                currentIsErr = true;
            }
        }

    };

    public void commitLastEntry() {
        if (currentBaos.size() > 0) {
            entries.add(new Entry(currentIsErr, currentBaos.toByteArray()));
            currentBaos.reset();
        }
    }

    public OutputStream getOut() {
        return out;
    }

    public OutputStream getErr() {
        return err;
    }

    public void write(OutputStream out, OutputStream err) throws IOException {
        for (Entry e : entries) {
            if (e.isErr) {
                err.write(e.data);
            } else {
                out.write(e.data);
            }
        }
    }
}
