package com.github.ruediste.remoteJUnit.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public class OutErrCombiningStreamTest {

    @Test
    public void test() throws IOException {
        OutErrCombiningStream str = new OutErrCombiningStream();

        byte[] hello = "Hello".getBytes("UTF-8");
        byte[] world = "World".getBytes("UTF-8");

        str.getOut().write(hello);
        str.getErr().write(world);
        str.commitLastEntry();

        assertEquals(2, str.entries.size());
        assertFalse(str.entries.get(0).isErr);
        assertArrayEquals(hello, str.entries.get(0).data);
        assertTrue(str.entries.get(1).isErr);
        assertArrayEquals(world, str.entries.get(1).data);

        str.write(System.out, System.out);
    }
}
