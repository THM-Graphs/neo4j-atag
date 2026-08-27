# `atag.model.meta.write` / `atag.model.meta.read`

## Description

Store the project model as graph data next to the instance data it describes, and read it
back. Each RAMEN concept becomes a `(:Meta:Concept)` node, each label a project uses for
that concept a `(:Meta:Type)` node refining it, and the RAMEN relations become
relationships between the concept nodes - carrying the very relationship types the
instance data uses.

Once written, an [import or export profile](profiles.html) can refer to it with
`model: 'meta'` instead of repeating the model in every call.

## Parameters

| name         | type | description                                          | default value |
|--------------|------|------------------------------------------------------|---------------|
| config       | map  | configuration holding a `model` entry (`write` only) | `{}`          |
|              |      |                                                      |               |
| return value | map  | counts of concepts and types, or the model itself    |               |

## Example

```cypher
CALL atag.model.meta.write({
  model: {content: ['Transcript'], annotation: ['Note'], entity: ['Person']}
}) YIELD value
RETURN value
```

```cypher
MATCH (t:Transcript {uuid: 'transcript-1'})
CALL atag.export.standoff_json.fromNode(t, {model: 'meta'}) YIELD value
RETURN value
```
