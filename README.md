jsonpps
======================================

A streaming JSON pretty printer that can format multi-GB input files.

Pretty prints a whitespace-separated sequence of JSON values.  Example:
```
$ echo 'false 0 "string" [1,2,3] {"a":4} null' | jsonpps
false
0
"string"
[ 1, 2, 3 ]
{
  "a" : 4
}
null
```

Why yet another JSON pretty printer?

* Processes very large JSON files in a memory-efficient way.  Does not read entire files into memory at once.

* Preserves the order of key/value pairs in JSON objects unlike, for example, `python -mjson.tool`.

* Accepts input that already has some formatting such as newline characters.

* Unless `--strict` is specified, accepts certain JavaScript syntax such as comments, unquoted keys, single quoted strings.  Example:

    ```
    $ echo "{key: 'value' /*inline comment*/} // line comment" | jsonpps
    {
      "key" : "value"
    }
    ```

Uses the [Jackson streaming JSON parser] (http://wiki.fasterxml.com/JacksonHome).

Usage
-----

```
$ jsonpps -h
usage:
    jsonpps              - pretty print stdin
    jsonpps -            - pretty print stdin
    jsonpps <file> ...   - pretty print file(s)

options:
    -o <file>            - output file
    --strict             - reject non-conforming json
```

Installation
------------

Requires Java and Maven.

```
# Download and build streaming jsonpps
git clone git@github.com:bazaarvoice/jsonpps.git
cd jsonpps
mvn clean package

# Install
sudo ln -s $(pwd)/jsonpps /usr/local/bin/
```

