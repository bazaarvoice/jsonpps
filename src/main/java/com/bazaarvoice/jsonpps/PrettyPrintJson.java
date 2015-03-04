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

import static com.fasterxml.jackson.core.JsonTokenId.ID_FIELD_NAME;
import static com.fasterxml.jackson.core.JsonTokenId.ID_START_ARRAY;
import static com.fasterxml.jackson.core.JsonTokenId.ID_START_OBJECT;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class PrettyPrintJson {
    private static final File STDINOUT = new File("-");

    private int flatten;
    private boolean sortKeys;
    private boolean strict;
    private boolean wrap;
    private InputStream stdin = System.in;
    private OutputStream stdout = System.out;

    public static void main(String[] args) throws Exception {
        try {
            ArgumentParser parser = ArgumentParsers.newArgumentParser("jsonpps")
                    .description("A streaming JSON pretty printer that can format multi-GB input files.")
                    .defaultHelp(true);
            parser.addArgument("-o", "--out")
                    .type(Arguments.fileType())
                    .setDefault(STDINOUT)
                    .help("output file");
            parser.addArgument("--flatten")
                    .metavar("N")
                    .type(Integer.class)
                    .setDefault(0)
                    .help("flatten the top-N levels of object/array structure");
            parser.addArgument("-i", "--in-place")
                    .action(Arguments.storeTrue())
                    .help("modify the original file(s)");
            parser.addArgument("-S", "--sort-keys")
                    .action(Arguments.storeTrue())
                    .help("emit objects with keys in sorted order. this increases memory requirements since objects must be buffered in memory.");
            parser.addArgument("--strict")
                    .action(Arguments.storeTrue())
                    .help("reject non-conforming json");
            parser.addArgument("--wrap")
                    .action(Arguments.storeTrue())
                    .help("wrap all output in a json array");
            parser.addArgument("--unwrap")
                    .action(Arguments.storeTrue())
                    .help("flatten the top level of object/array structure");
            parser.addArgument("in")
                    .nargs("*")
                    .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyIsFile().verifyCanRead())
                    .setDefault(new File[]{STDINOUT})
                    .help("input file(s)");
            Namespace ns;
            try {
                ns = parser.parseArgs(args);
            } catch (ArgumentParserException e) {
                parser.handleError(e);
                System.exit(2);
                return;
            }

            PrettyPrintJson jsonpp = new PrettyPrintJson();
            File outputFile = ns.get("out");
            List<File> inputFiles = ns.getList("in");
            boolean inPlace = ns.getBoolean("in_place");
            jsonpp.setFlatten(ns.getInt("flatten"));
            jsonpp.setSortKeys(ns.getBoolean("sort_keys"));
            jsonpp.setStrict(ns.getBoolean("strict"));
            jsonpp.setWrap(ns.getBoolean("wrap"));
            if (ns.getBoolean("unwrap")) {
                jsonpp.setFlatten(1);
            }

            if (!inPlace) {
                // Pretty print all input files to a single output
                jsonpp.prettyPrint(inputFiles, outputFile);

            } else {
                // Pretty print all input files back to themselves.
                if (outputFile != STDINOUT) {  // use "!=" not "!.equals()" since default is ok but "-o -" is not.
                    System.err.println("error: -o and --in-place are mutually exclusive");
                    System.exit(2);
                }
                if (inputFiles.isEmpty()) {
                    System.err.println("error: --in-place requires at least one input file");
                    System.exit(2);
                }
                if (inputFiles.contains(STDINOUT)) {
                    System.err.println("error: --in-place cannot operate on stdin");
                    System.exit(2);
                }
                for (File inputFile : inputFiles) {
                    jsonpp.prettyPrint(inputFile, inputFile);
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println(t.toString());
            System.exit(1);
        }
    }

    public void setFlatten(int flatten) {
        this.flatten = flatten;
    }

    public void setSortKeys(boolean sortKeys) {
        this.sortKeys = sortKeys;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    public void setStdin(InputStream stdin) {
        this.stdin = stdin;
    }

    public void setStdout(OutputStream stdout) {
        this.stdout = stdout;
    }

    public void prettyPrint(File inputFile, File outputFile) throws IOException {
        prettyPrint(Collections.singletonList(inputFile), outputFile);
    }

    public void prettyPrint(List<File> inputFiles, File outputFile) throws IOException {
        JsonFactory factory = new JsonFactory();
        factory.disable(JsonFactory.Feature.INTERN_FIELD_NAMES);
        if (!strict) {
            factory.enable(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER);
            factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
            factory.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
            factory.enable(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS);
            factory.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            factory.enable(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
            factory.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        }

        ObjectMapper mapper = null;
        if (sortKeys) {
            mapper = new ObjectMapper(factory);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            mapper.disable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);
        }

        // Open the output stream and create the Json emitter.
        JsonGenerator generator;
        File tempOutputFile = null;
        if (STDINOUT.equals(outputFile)) {
            generator = factory.createGenerator(stdout, JsonEncoding.UTF8);
        } else if (!caseInsensitiveContains(inputFiles, outputFile)) {
            generator = factory.createGenerator(outputFile, JsonEncoding.UTF8);
        } else {
            // Writing to an input file.. use a temp file to stage the output until we're done.
            tempOutputFile = getTemporaryFileFor(outputFile);
            generator = factory.createGenerator(tempOutputFile, JsonEncoding.UTF8);
        }
        try {
            // Separate top-level objects by a newline in the output.
            String newline = System.getProperty("line.separator");
            generator.setPrettyPrinter(getPrettyPrinter(newline));

            if (wrap) {
                generator.writeStartArray();
            }

            for (File inputFile : inputFiles) {
                JsonParser parser;
                if (STDINOUT.equals(inputFile)) {
                    parser = factory.createParser(stdin);
                } else {
                    parser = factory.createParser(inputFile);
                }
                try {
                    while (parser.nextToken() != null) {
                        copyCurrentStructure(parser, mapper, 0, generator);
                    }
                } finally {
                    parser.close();
                }
            }

            if (wrap) {
                generator.writeEndArray();
            }

            generator.writeRaw(newline);
        } finally {
            generator.close();
        }
        if (tempOutputFile != null && !tempOutputFile.renameTo(outputFile)) {
            System.err.println("error: unable to rename temporary file to output: " + outputFile);
            System.exit(1);
        }
    }

    private void copyCurrentStructure(JsonParser parser, ObjectMapper mapper, int depth, JsonGenerator generator) throws IOException {
        // Avoid using the mapper to parse the entire input until we absolutely must.  This allows pretty
        // printing huge top-level arrays (that wouldn't fit in memory) containing smaller objects (that
        // individually do fit in memory) where the objects are printed with sorted keys.
        JsonToken t = parser.getCurrentToken();
        if (t == null) {
            generator.copyCurrentStructure(parser);  // Will report the error of a null token.
            return;
        }
        int id = t.id();
        if (id == ID_FIELD_NAME) {
            if (depth > flatten) {
                generator.writeFieldName(parser.getCurrentName());
            }
            t = parser.nextToken();
            id = t.id();
        }
        switch (id) {
            case ID_START_OBJECT:
                if (sortKeys && depth >= flatten) {
                    // Load the entire object in memory so we can sort its keys and serialize it back out.
                    mapper.writeValue(generator, parser.readValueAs(Map.class));
                } else {
                    // Don't load the whole object into memory.  Copy it in a memory-efficient streaming fashion.
                    if (depth >= flatten) {
                        generator.writeStartObject();
                    }
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        copyCurrentStructure(parser, mapper, depth + 1, generator);
                    }
                    if (depth >= flatten) {
                        generator.writeEndObject();
                    }
                }
                break;
            case ID_START_ARRAY:
                // Don't load the whole array into memory.  Copy it in a memory-efficient streaming fashion.
                if (depth >= flatten) {
                    generator.writeStartArray();
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    copyCurrentStructure(parser, mapper, depth + 1, generator);
                }
                if (depth >= flatten) {
                    generator.writeEndArray();
                }
                break;
            default:
                generator.copyCurrentEvent(parser);
                break;
        }
    }

    private boolean caseInsensitiveContains(Collection<File> srcs, File dest) throws IOException {
        for (File src : srcs) {
            if (!STDINOUT.equals(src) && src.getCanonicalPath().equalsIgnoreCase(dest.getCanonicalPath())) {
                return true;
            }
        }
        return false;
    }

    private File getTemporaryFileFor(File file) {
        // The temporary file must exist in the same directory as the destination file so we can
        // reliably rename it at the end w/o copying across volumes.  Use a secure random UUID to
        // name the file since there is mathematically no realistic chance of collisions.
        String randomSuffix = UUID.randomUUID().toString().replace("-", "");
        File tempFile = new File(file.getParentFile(), "_" + file.getName() + "." + randomSuffix);
        tempFile.deleteOnExit();
        return tempFile;
    }
    
    private PrettyPrinter getPrettyPrinter(String lf) {
		DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter(lf);
		prettyPrinter.indentArraysWith(new Indentation());
		prettyPrinter.indentObjectsWith(new Indentation());
		return prettyPrinter;
    }
}
