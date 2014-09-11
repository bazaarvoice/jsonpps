/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.jsonpps;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class PrettyPrintJsonTest {

    @Test
    public void testMultiple() throws Exception {
        doTest("1 2 3", "1\n2\n3\n");
    }

    @Test
    public void testNull() throws Exception {
        doTest("null", "null\n");
    }

    @Test
    public void testBoolean() throws Exception {
        doTest("true", "true\n");
        doTest("false", "false\n");
    }

    @Test
    public void testInteger() throws Exception {
        doTest("1", "1\n");
        doTest("01", "1\n");
        doTest(Long.toString(Long.MIN_VALUE), Long.MIN_VALUE + "\n");
        doTest(BigInteger.TEN.pow(123).toString(), BigInteger.TEN.pow(123) + "\n");
    }

    @Test
    public void testDouble() throws Exception {
        doTest("1.23456789", "1.23456789\n");
    }

    @Test
    public void testString() throws Exception {
        doTest("\"hello\tw\u00f3rld\n\"", "\"hello\\tw\u00f3rld\\n\"\n");
    }

    @Test
    public void testArray() throws Exception {
        doTest("[1,2,\"three\"]", "[ 1, 2, \"three\" ]\n");
        doTest("[{\"b\":2,\"a\":1,\"C\":3},true]", "[ {\n  \"b\" : 2,\n  \"a\" : 1,\n  \"C\" : 3\n}, true ]\n");
    }

    @Test
    public void testObject() throws Exception {
        doTest("{\"key1\":\"value1\",\"key2\":2}", "{\n  \"key1\" : \"value1\",\n  \"key2\" : 2\n}\n");
        doTest("{\"b\":2,\"a\":1,\"C\":3}", "{\n  \"b\" : 2,\n  \"a\" : 1,\n  \"C\" : 3\n}\n");
    }

    @Test
    public void testNewlinesInInput() throws Exception {
        doTest("[\n1,\n2\n,\"three\"\n]", "[ 1, 2, \"three\" ]\n");
    }

    @Test
    public void testSingleQuotes() throws Exception {
        doTest("'string' {'key1':'value1'}", "\"string\"\n{\n  \"key1\" : \"value1\"\n}\n");
    }

    @Test
    public void testMissingQuotes() throws Exception {
        doTest("{key1:'value1'}", "{\n  \"key1\" : \"value1\"\n}\n");
    }

    @Test
    public void testSortKeys() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();
        jsonpps.setSortKeys(true);
        doTest("[3,2,1]", "[ 3, 2, 1 ]\n");
        doTest(jsonpps, "{\"b\":2,\"a\":1,\"C\":3}", "{\n  \"C\" : 3,\n  \"a\" : 1,\n  \"b\" : 2\n}\n");
        doTest(jsonpps, "[{\"b\":2,\"a\":1,\"C\":3},true]", "[ {\n  \"C\" : 3,\n  \"a\" : 1,\n  \"b\" : 2\n}, true ]\n");
    }

    @Test
    public void testFlattenArray() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();
        jsonpps.setFlatten(2);

        doTest(jsonpps, "[3,2,[1],[[0]]]", "3\n2\n1\n[ 0 ]\n");
    }

    @Test
    public void testFlattenObject() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();

        jsonpps.setFlatten(1);
        doTest(jsonpps, "{a:1}", "1\n");
        doTest(jsonpps, "{a:1,b:{c:2}}", "1\n{\n  \"c\" : 2\n}\n");

        jsonpps.setFlatten(2);
        jsonpps.setSortKeys(true); // Sorting should only occur at nesting depths that aren't flattened.
        doTest(jsonpps, "{a:{b:{c:{d:1,E:2}}},F:2,g:[3,4]}", "{\n  \"c\" : {\n    \"E\" : 2,\n    \"d\" : 1\n  }\n}\n2\n3\n4\n");
    }

    @Test
    public void testWrap() throws Exception {
        PrettyPrintJson jsonpps = new PrettyPrintJson();
        jsonpps.setWrap(true);

        doTest(jsonpps, "1 2 3 [4]", "[ 1, 2, 3, [ 4 ] ]\n");
    }

    private void doTest(String input, String output) throws Exception {
        doTest(new PrettyPrintJson(), input, output);
    }

    private void doTest(PrettyPrintJson jsonpps, String input, String output) throws Exception {
        output = output.replaceAll("\n", System.getProperty("line.separator"));
        InputStream stdin = new ByteArrayInputStream(input.getBytes("UTF-8"));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        jsonpps.setStdin(stdin);
        jsonpps.setStdout(stdout);
        jsonpps.prettyPrint(new File("-"), new File("-"));

        assertEquals(output, stdout.toString("UTF-8"));
    }
}
