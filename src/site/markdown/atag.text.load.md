# `atag.text.load`

## Description

A function that loads the contents of a given URI into a string. 
This runs a HTTP get command.
In case something goes wrong, an error is thrown. 
This can e.g. happen if the URI is not reachable or the file does not exist.

## Parameters

| name         | type   | description           | default value |
|--------------|--------|-----------------------|---------------|
| uri          | String | URI to be loaded      |               |
|              |        |                       |               |
| return value | String | contents of that file |               |

## Example

```cypher
RETURN atag.text.load('http://www.regesta-imperii.de/id/1316-05-14_1_0_8_0_0_1_a')
```