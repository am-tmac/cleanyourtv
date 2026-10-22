package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MiniJsonTest {
    @Test public void parsesObjectsArraysAndScalars() {
        Object v = MiniJson.parse("{\"a\":1.5,\"b\":[true,false,null],\"c\":\"x\\/y\\u5385\",\"d\":{\"e\":-2e1}}");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(1.5, (Double) m.get("a"), 1e-9);
        List<?> b = (List<?>) m.get("b");
        assertEquals(Boolean.TRUE, b.get(0));
        assertEquals(Boolean.FALSE, b.get(1));
        assertNull(b.get(2));
        assertEquals("x/y\u5385", m.get("c"));
        assertEquals(-20.0, (Double) ((Map<?, ?>) m.get("d")).get("e"), 1e-9);
    }

    @Test public void garbageAndHtmlReturnNull() {
        assertNull(MiniJson.parse("<!DOCTYPE html><html>Just a moment...</html>"));
        assertNull(MiniJson.parse("{\"a\":"));
        assertNull(MiniJson.parse(""));
        assertNull(MiniJson.parse(null));
        assertNull(MiniJson.parse("{\"a\":1} trailing"));
    }

    @Test public void deepNestingIsRejectedNotOverflowed() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 5000; i++) s.append('[');
        assertNull(MiniJson.parse(s.toString()));
    }

    @Test public void helpersReadTypedFields() {
        Map<String, Object> m = MiniJson.object(MiniJson.parse("{\"s\":\"v\",\"n\":3,\"o\":{\"k\":1}}"));
        assertEquals("v", MiniJson.string(m, "s"));
        assertNull(MiniJson.string(m, "n"));
        assertEquals(3.0, MiniJson.number(m, "n"), 1e-9);
        assertTrue(Double.isNaN(MiniJson.number(m, "s")));
        assertEquals(1.0, MiniJson.number(MiniJson.object(m.get("o")), "k"), 1e-9);
        assertNull(MiniJson.object("not a map"));
    }
}
