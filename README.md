jsonpp
======================================

A streaming JSON pretty printer that can format multi-GB input files.

Pretty prints a whitespace-separated sequence of JSON values.  Example:
```
$ echo 'false 0 "string" [1,2,3] {"a":4} null' | jsonpp
false
0
"string"
[ 1, 2, 3 ]
{
  "a" : 4
}
null
```

Unless `--strict` is specified, accepts certain JavaScript syntax such as comments, unquoted keys, single quoted strings.  Example:

```
$ echo "{key: 'value' /*inline comment*/} // line comment" | jsonpp
{
  "key" : "value"
}
```

Preserves the order of key/value pairs in Json objects unlike, for example, `python -mjson.tool`.

Uses the [Jackson streaming JSON parser] (http://wiki.fasterxml.com/JacksonHome).

Usage
-----

```
$ jsonpp -h
usage: jsonpp              - pretty print stdin
       jsonpp -            - pretty print stdin
       jsonpp <file> ...   - pretty print file(s)
       -o <file>           - output file
       --strict            - reject non-conforming json
```

Installation
------------

Requires Java and Maven.

```
# Download and build streaming jsonpp
git clone git@github.com:bazaarvoice/jsonpp.git
cd jsonpp
mvn clean package

# Install
sudo ln -s $(pwd)/jsonpp /usr/local/bin/
```

