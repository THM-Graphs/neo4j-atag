# `atag.chains.characterChain`

## Description

This procedure creates a chain of nodes, each representing a character of the input string. 
Optionally the chain can be enriched with `startIndex` and `endIndex` properties indicating the position of the character in the input string.

## Parameters

| name         | type    | description                                         | default value |
|--------------|---------|-----------------------------------------------------|---------------|
| text         | String  | string to be used for building a chain of nodes     |               |
| applyIndexes | boolean | if true, `startIndex/endIndex` properties are added | `true`        |
|              |         |                                                     |               |
| return value | Path    | a path holding the created chain                    |               |

## Examples

```cypher
CALL atag.chains.characterChain('what a nice text') YIELD path RETURN path;
```

The text can be taken from a node property:

```cypher
match (t:Text)
where t.text is not null
with t
CALL atag.chains.characterChain(t.text) yield path return path;
```