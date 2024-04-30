# `atag.chains.chain`

## Description
A generic procedure to build a chain of nodes offering lots of flexibility.

## Parameters

| name                 | type    | description                                     | default value |
|----------------------|---------|-------------------------------------------------|---------------|
| text                 | string  | string to be used for building a chain of nodes |               |
| regex                | string  | separator regex pattern                         |               |
| label                | string  | label for new nodes                             |               |
| relType              | string  | relationship type used for the chain            |               |
| applyIndexProperties | boolean | whether to add startIndex/endIndex properties   |               |
|                      |         |                                                 |               |
| return value         | Path    | a path holding the created chain                |               |

NOTE: the regex parameter uses lookahead/lookbehind notation, see https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html

## Examples

```cypher
CALL atag.chains.chain('what a nice text', '((?<=\\s)|(?=\\s))', 'Character', 'NEXT_CHARACTER', false) YIELD path RETURN path
```
