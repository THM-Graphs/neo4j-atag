# `atag.deterministicUUID`

## Description

This function generates a name-based UUID (version 3) from the UTF-8 bytes of an input string.
The same input always produces the same UUID, which is returned as a string.
The input is used as-is, without trimming or changing its case.

## Parameters

| name         | type   | description                            | default value |
|--------------|--------|----------------------------------------|---------------|
| input        | String | string used to generate the UUID       |               |
|              |        |                                        |               |
| return value | String | UUID derived from the input string     |               |

## Example

```cypher
RETURN atag.deterministicUUID('document-42') AS uuid;
```
