package com.bazaarvoice.jsonpp;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JsonPP {
    public static void main(String[] args) throws Exception {
        try {
            List<String> argv = new ArrayList<String>(Arrays.asList(args));
            if (argv.size() == 1 && "-h".equals(argv.get(0))) {
                System.err.println("usage: jsonpp              - pretty print stdin");
                System.err.println("       jsonpp -            - pretty print stdin");
                System.err.println("       jsonpp <file> ...   - pretty print file(s)");
                System.err.println("       -o <file>           - output file");
                System.err.println("       --strict            - reject non-conforming json");
                System.exit(2);
            }

            JsonFactory factory = new JsonFactory();
            factory.disable(JsonFactory.Feature.INTERN_FIELD_NAMES);

            // By default, configure the pretty printer to be as permissive as possible.
            if (!argv.remove("--strict")) {
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
            int outputOpt = argv.indexOf("-o");
            if (outputOpt != -1) {
                if (outputOpt + 1 == argv.size()) {
                    System.err.println("error: -o requires a filename argument");
                    System.exit(2);
                }
                String outputFilename = argv.get(outputOpt + 1);
                argv.subList(outputOpt, outputOpt + 2).clear();
                generator = factory.createGenerator(new File(outputFilename), JsonEncoding.UTF8);
            } else {
                generator = factory.createGenerator(System.out, JsonEncoding.UTF8);
            }
            try {
                String newline = System.getProperty("line.separator");
                generator.setPrettyPrinter(new DefaultPrettyPrinter(newline));

                if (argv.isEmpty()) {
                    argv = Collections.singletonList("-");
                }

                for (String filename : argv) {
                    JsonParser parser;
                    if ("-".equals(filename)) {
                        parser = factory.createParser(System.in);
                    } else {
                        parser = factory.createParser(new File(filename));
                    }

                    while (parser.nextToken() != null) {
                        generator.copyCurrentStructure(parser);
                    }

                    parser.close();
                }

                generator.writeRaw(newline);
            } finally {
                generator.close();
            }

        } catch (Throwable t) {
            System.err.println(t);
            System.exit(1);
        }
    }
}
