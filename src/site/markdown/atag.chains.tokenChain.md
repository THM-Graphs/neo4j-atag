# `atag.chains.tokenChain`

## Description
Tokenizes a string and creates a chain of nodes, one for each token.
Uses regex `((?<=\W)|(?=\W))` for identifying tokens.

## Parameters

| name         | type    | description                                         | default value |
|--------------|---------|-----------------------------------------------------|---------------|
| text         | String  | string to be used for building a chain of nodes     |               |
| applyIndexes | boolean | if true, `startIndex/endIndex` properties are added | `true`        |
|              |         |                                                     |               |
| return value | Path    | a path holding the created chain                    |               |

## Examples

```cypher
CALL atag.chains.tokenChain('what a nice text') YIELD path RETURN path;
```

```cypher
match (t:Text)
where t.text is not null
with t
CALL atag.chains.tokenChain(t.text) yield path return path;
```