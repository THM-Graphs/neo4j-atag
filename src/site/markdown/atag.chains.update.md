# `atag.chains.tokenChain`

## Description

Partially updates a chain of tokens or characters

## Parameters

| name         | type          | description                                                                     | default value |
|--------------|---------------|---------------------------------------------------------------------------------|---------------|
| uuidStart    | String        | uuid of the node located before the changeset                                   |               |
| uuidEnd      | String        | uuid of the node located after the changeset                                    |               |
| replacement  | array of maps | a list of nodes described by their properties.<br/>each map must contain a uuid |               |
| config       | map           | configuration settings, see table below for details                             | `{}`          |
| return value | Path          | a path representing the changeset, excluding uuidStart and uuidEnd nodes        |               |

## Configuration Settings

| name             | description                                              | default value    |
|------------------|----------------------------------------------------------|------------------|
| textLabel        | label to be used for entry point nodes, aka `Text` nodes | `Text`           |
| elementLabel     | label to be used for chain element nodes                 | `Character`      |
| relationshipType | relationship type interconnection the element nodes      | `NEXT_CHARACTER` |

## Examples

Assume this sample graph:

```cypher
CREATE (t:Text{uuid:$uuidText})
CREATE (s:Token{uuid:$uuidStart})
CREATE (m1:Token{uuid:$uuidMiddle1, tagName:'a'})
CREATE (m2:Token{uuid:$uuidMiddle2, tagName:'b'})
CREATE (e:Token{uuid:$uuidEnd})
CREATE (t)-[:NEXT_TOKEN]->(s)
CREATE (s)-[:NEXT_TOKEN]->(m1)
CREATE (m1)-[:NEXT_TOKEN]->(m2)
CREATE (m2)-[:NEXT_TOKEN]->(e)
```

```cypher
CALL atag.chains.update($uuidText, $uuidStart, $uuidEnd, [
    {
        uuid: $uuidMiddle1,
        tagName: 'a'
    },
    {
        uuid:  $uuidMiddle2,
        tagName: 'c'
    }
], {
    textLabel: "Text",
    elementLabel: "Token",
    relationshipType: "NEXT_TOKEN"
}) YIELD path RETURN path
```