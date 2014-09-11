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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PrettyPrintJson {
    private static final File STDINOUT = new File("-");

    public static void main(String[] args) throws Exception {
        try {
            ArgumentParser parser = ArgumentParsers.newArgumentParser("jsonpps")
                    .description("A streaming JSON pretty printer that can format multi-GB input files.")
                    .defaultHelp(true);
            parser.addArgument("-o", "--out")
                    .type(Arguments.fileType())
                    .setDefault(STDINOUT)
                    .help("output file");
            parser.addArgument("-i", "--in-place")
                    .action(Arguments.storeTrue())
                    .help("modify the original file(s)");
            parser.addArgument("--strict")
                    .action(Arguments.storeTrue())
                    .help("reject non-conforming json");
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

            File outputFile = ns.get("out");
            List<File> inputFiles = ns.getList("in");
            boolean inPlace = ns.getBoolean("in_place");
            boolean strict = ns.getBoolean("strict");

            if (!inPlace) {
                // Pretty print all input files to a single output
                prettyPrint(inputFiles, outputFile, strict, System.in, System.out);

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
                    prettyPrint(inputFile, inputFile, strict, null, null);
                }
            }

        } catch (Throwable t) {
            System.err.println(t.toString());
            System.exit(1);
        }
    }

    static void prettyPrint(File inputFile, File outputFile, boolean strict,
                            InputStream stdin, OutputStream stdout) throws IOException {
        prettyPrint(Collections.singletonList(inputFile), outputFile, strict, stdin, stdout);
    }

    static void prettyPrint(List<File> inputFiles, File outputFile, boolean strict,
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
            generator.setPrettyPrinter(new DefaultPrettyPrinter(newline));

            for (File inputFile : inputFiles) {
                JsonParser parser;
                if (STDINOUT.equals(inputFile)) {
                    parser = factory.createParser(stdin);
                } else {
                    parser = factory.createParser(inputFile);
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

    private static boolean caseInsensitiveContains(Collection<File> srcs, File dest) throws IOException {
        for (File src : srcs) {
            if (!STDINOUT.equals(src) && src.getCanonicalPath().equalsIgnoreCase(dest.getCanonicalPath())) {
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
