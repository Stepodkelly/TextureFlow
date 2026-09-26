package com.textureflow.intelligence.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NoModelPortTest {
    @Test
    public void tokenCountUsesCharHeuristic() {
        NoModelPort port = new NoModelPort();
        assertEquals(0, port.tokenCount(""));
        assertTrue(port.tokenCount("word word word word") >= 4);
    }

    @Test(expected = IllegalStateException.class)
    public void generateThrowsWhenNoModel() {
        new NoModelPort().generate(new ModelRequest("hi", 8, 0.2f));
    }
}
