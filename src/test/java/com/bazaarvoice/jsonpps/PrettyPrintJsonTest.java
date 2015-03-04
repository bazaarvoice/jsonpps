/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.jsonpps;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class PrettyPrintJsonTest {

    @Test
    public void testMultiple() throws Exception {
        doTest("1 2 3");
    }

    @Test
    public void testNull() throws Exception {
        doTest("null");
    }

    @Test
    public void testBoolean() throws Exception {
        doTest("true");
        doTest("false", 2);
    }

    @Test
    public void testInteger() throws Exception {
        doTest("1");
        doTest("01", 2);
        doTest(Long.toString(Long.MIN_VALUE), 3);
        doTest(BigInteger.TEN.pow(123).toString(), 4);
    }

    @Test
    public void testDouble() throws Exception {
        doTest("1.23456789");
    }

    @Test
    public void testString() throws Exception {
        doTest("\"hello w\u00f3rld\n\"");
    }

    @Test
    public void testArray() throws Exception {
        doTest("[1,2,\"three\"]");
        doTest("[{\"b\":2,\"a\":1,\"C\":3},true]", 2);
    }

    @Test
    public void testObject() throws Exception {
        doTest("{\"key1\":\"value1\",\"key2\":2}");
        doTest("{\"b\":2,\"a\":1,\"C\":3}", 2);
    }

    @Test
    public void testNewlinesInInput() throws Exception {
        doTest("[\n1,\n2\n,\"three\"\n]");
    }

    @Test
    public void testSingleQuotes() throws Exception {
        doTest("'string' {'key1':'value1'}");
    }

    @Test
    public void testMissingQuotes() throws Exception {
        doTest("{key1:'value1'}");
    }

    @Test
    public void testSortKeys() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();
        jsonpps.setSortKeys(true);
        doTest("[3,2,1]");
        doTest(jsonpps, "{\"b\":2,\"a\":1,\"C\":3}", 2);
        doTest(jsonpps, "[{\"b\":2,\"a\":1,\"C\":3},true]", 3);
    }

    @Test
    public void testFlattenArray() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();
        jsonpps.setFlatten(2);

        doTest(jsonpps, "[3,2,[1],[[0]]]");
    }

    @Test
    public void testFlattenObject() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();

        jsonpps.setFlatten(1);
        doTest(jsonpps, "{a:1}");
        doTest(jsonpps, "{a:1,b:{c:2}}", 2);

        jsonpps.setFlatten(2);
        jsonpps.setSortKeys(true); // Sorting should only occur at nesting depths that aren't flattened.
        doTest(jsonpps, "{a:{b:{c:{d:1,E:2}}},F:2,g:[3,4]}", 3);
    }

    @Test
    public void testWrap() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();
        jsonpps.setWrap(true);

        doTest(jsonpps, "1 2 3 [4]");
    }

    private void doTest(String input) throws Exception {
        doTest(new PrettyPrintJson(), input);
    }

    private void doTest(String input, Integer index) throws Exception {
        doTest(new PrettyPrintJson(), input, index);
    }

    private void doTest(PrettyPrintJson jsonpps, String input) throws Exception {
    	doTest(jsonpps, input, (Integer) null);
    }

    private void doTest(PrettyPrintJson jsonpps, String input, Integer index) throws Exception {
    	String method = null;
    	Iterator<StackTraceElement> stackTrace = Arrays.asList(new Throwable().getStackTrace()).iterator();
    	while(method == null) {
    		StackTraceElement call = stackTrace.next();
    		if(!call.getMethodName().equals("doTest")) {
    			method = call.getMethodName();
    		}
    	}
    	String filename = method+(index != null ? "-"+index : "")+".output.json";
    	String output = IOUtils.toString(getClass().getResourceAsStream("/"+filename));
        InputStream stdin = new ByteArrayInputStream(input.getBytes("UTF-8"));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        jsonpps.setStdin(stdin);
        jsonpps.setStdout(stdout);
        jsonpps.prettyPrint(new File("-"), new File("-"));

        assertEquals(output, stdout.toString("UTF-8"));
    }
}
