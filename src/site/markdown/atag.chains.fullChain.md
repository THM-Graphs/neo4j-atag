# `atag.chains.fullChain`

## Description
The most complete procedure, creates both token and character chain.
Delegates internally to [atag.chains.tokenChain](atag.chains.tokenChain.html) and [atag.chain.characterChain](atag.chains.characterChain.html).
Interconnects the two chains appropriately.

## Parameters

| name         | type    | description                                                            | default value |
|--------------|---------|------------------------------------------------------------------------|---------------|
| start        | Node    | start node holding the text in a property                              |               |
| propertyKey  | string  | property key                                                           |               |
| applyIndexes | boolean | if true, `startIndex/endIndex` properties are added to character nodes | `false`       |
|              |         |                                                                        |               |
| return value | void    |                                                                        |               |

## Example

```cypher
CREATE (s:Text{text:'What a nice text'})
WITH s
CALL atag.chains.fullChain(s, 'text')
RETURN s
```
