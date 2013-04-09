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

public class PrettyPrintJson {
    public static void main(String[] args) throws Exception {
        try {
            List<String> argv = new ArrayList<String>(Arrays.asList(args));
            if (argv.size() == 1 && "-h".equals(argv.get(0))) {
                System.err.println("usage:");
                System.err.println("    jsonpps              - pretty print stdin");
                System.err.println("    jsonpps -            - pretty print stdin");
                System.err.println("    jsonpps <file> ...   - pretty print file(s)");
                System.err.println();
                System.err.println("options:");
                System.err.println("    -o <file>            - output file");
                System.err.println("    --strict             - reject non-conforming json");
                System.exit(2);
            }

            // By default, configure the pretty printer to be as permissive as possible.
            boolean strict = argv.remove("--strict");

            // Parse output file command-line argument
            String outputFilename = "-";
            int outputOpt = argv.indexOf("-o");
            if (outputOpt != -1) {
                if (outputOpt + 1 == argv.size()) {
                    System.err.println("error: -o requires a filename argument");
                    System.exit(2);
                }
                outputFilename = argv.get(outputOpt + 1);
                argv.subList(outputOpt, outputOpt + 2).clear();
            }

            // If no input files, parse stdin
            if (argv.isEmpty()) {
                argv = Collections.singletonList("-");
            }

            prettyPrint(argv, outputFilename, strict, System.in, System.out);

        } catch (Throwable t) {
            System.err.println(t);
            System.exit(1);
        }
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
        if ("-".equals(outputFilename)) {
            generator = factory.createGenerator(stdout, JsonEncoding.UTF8);
        } else {
            generator = factory.createGenerator(new File(outputFilename), JsonEncoding.UTF8);
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
    }
}
