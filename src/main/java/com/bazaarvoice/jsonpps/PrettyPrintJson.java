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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PrettyPrintJson {
    public static void main(String[] args) throws Exception {
        try {
            List<String> argv = new ArrayList<String>(Arrays.asList(args));
            if (argv.size() == 1 && ("-h".equals(argv.get(0)) || "--help".equals(argv.get(0)))) {
                System.err.println("usage:");
                System.err.println("    jsonpps              - pretty print stdin");
                System.err.println("    jsonpps -            - pretty print stdin");
                System.err.println("    jsonpps <file> ...   - pretty print file(s)");
                System.err.println();
                System.err.println("options:");
                System.err.println("    -o <file>            - output file");
                System.err.println("    --in-place | -i      - modify the original file");
                System.err.println("    --strict             - reject non-conforming json");
                System.err.println("    --                   - stop processing options");
                System.exit(2);
            }

            // Only arguments before a "--" option may be treated as option flags
            List<String> options = argv;
            int lastOptionIndex = argv.indexOf("--");
            if (lastOptionIndex != -1) {
                argv.remove(lastOptionIndex);
                options = argv.subList(0, lastOptionIndex);  // changes to "options" will also modify "argv"
            }

            // By default, configure the pretty printer to be as permissive as possible.
            boolean strict = options.remove("--strict");

            // Parse output file command-line argument
            String outputFilename = "-";
            int outputOpt = options.indexOf("-o");
            if (outputOpt != -1) {
                if (outputOpt + 1 == options.size()) {
                    System.err.println("error: -o requires a filename argument");
                    System.exit(2);
                }
                outputFilename = options.get(outputOpt + 1);
                options.subList(outputOpt, outputOpt + 2).clear();
            }

            // Write to a temp file then rename it over the original file?
            boolean inPlace = options.remove("--in-place") || options.remove("-i");

            for (String option : options) {
                if (option.startsWith("-")) {
                    System.err.println("error: unknown option: " + option);
                    System.exit(2);
                }
            }

            // If no input files, parse stdin
            List<String> inputFilenames = argv.isEmpty() ? Collections.singletonList("-") : argv;

            if (!inPlace) {
                // Pretty print all input files to a single output
                prettyPrint(inputFilenames, outputFilename, strict, System.in, System.out);

            } else {
                // Pretty print all input files back to themselves.
                if (outputOpt != -1) {
                    System.err.println("error: -o and --in-place are mutually exclusive");
                    System.exit(2);
                }
                if (inputFilenames.isEmpty()) {
                    System.err.println("error: --in-place requires at least one input file");
                    System.exit(2);
                }
                if (inputFilenames.contains("-")) {
                    System.err.println("error: --in-place cannot operate on stdin");
                    System.exit(2);
                }
                for (String inputFilename : inputFilenames) {
                    prettyPrint(inputFilename, inputFilename, strict, null, null);
                }
            }

        } catch (Throwable t) {
            System.err.println(t);
            System.exit(1);
        }
    }

    static void prettyPrint(String inputFilename, String outputFilename, boolean strict,
                            InputStream stdin, OutputStream stdout) throws IOException {
        prettyPrint(Collections.singleton(inputFilename), outputFilename, strict, stdin, stdout);
    }

    static void prettyPrint(Collection<String> inputFilenames, String outputFilename, boolean strict,
                            InputStream stdin, OutputStream stdout)
            throws IOException {
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

        // Open the output stream and create the Json emitter.
        JsonGenerator generator;
        File tempOutputFile = null, outputFile = null;
        if ("-".equals(outputFilename)) {
            generator = factory.createGenerator(stdout, JsonEncoding.UTF8);
        } else if (!caseInsensitiveContains(inputFilenames, outputFilename)) {
            generator = factory.createGenerator(new File(outputFilename), JsonEncoding.UTF8);
        } else {
            // Writing to an input file.. use a temp file to stage the output until we're done.
            outputFile = new File(outputFilename);
            tempOutputFile = getTemporaryFileFor(outputFile);
            generator = factory.createGenerator(tempOutputFile, JsonEncoding.UTF8);
        }
        try {
            // Separate top-level objects by a newline in the output.
            String newline = System.getProperty("line.separator");
            generator.setPrettyPrinter(new DefaultPrettyPrinter(newline));

            for (String inputFilename : inputFilenames) {
                JsonParser parser;
                if ("-".equals(inputFilename)) {
                    parser = factory.createParser(stdin);
                } else {
                    parser = factory.createParser(new File(inputFilename));
                }
                try {
                    while (parser.nextToken() != null) {
                        generator.copyCurrentStructure(parser);
                    }
                } finally {
                    parser.close();
                }
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

    private static boolean caseInsensitiveContains(Collection<String> strings1, String string2) {
        for (String string1 : strings1) {
            if (string1.equalsIgnoreCase(string2)) {
                return true;
            }
        }
        return false;
    }

    private static File getTemporaryFileFor(File file) {
        // The temporary file must exist in the same directory as the destination file so we can
        // reliably rename it at the end w/o copying across volumes.  Use a secure random UUID to
        // name the file since there is mathematically no realistic chance of collisions.
        String randomSuffix = UUID.randomUUID().toString().replace("-", "");
        File tempFile = new File(file.getParentFile(), "_" + file.getName() + "." + randomSuffix);
        tempFile.deleteOnExit();
        return tempFile;
    }
}
