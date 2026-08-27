# `atag.export.standoff_json.*` / `atag.export.standoff_xml.*`

## Description

Export a subgraph as stand-off JSON or as generic stand-off XML: the text and its
annotations are kept apart, without committing to a markup vocabulary such as TEI. Both
formats render the same mapped document model, so they agree on how the graph is
interpreted - anchors nest along `PART_OF`, annotations sit on the anchor or annotation
they hang off, and annotations are ordered by `startIndex`.

Each format comes in two flavours: `fromNode` traverses from a start node according to the
[export profile](profiles.html), `list` takes the nodes and relationships the caller has
already selected.

## Example

```cypher
MATCH (t:Text {uuid: 'text-1'})
CALL atag.export.standoff_json.fromNode(t, {}) YIELD value
RETURN value
```

```json
{
  "uuid": "text-1",
  "text": "Hildegard writes to Berthold.",
  "annotations": [
    {"uuid": "a-1", "type": "person-reference", "startIndex": 0, "endIndex": 9,
     "annotations": [{"uuid": "c-1", "type": "commentary"}]}
  ]
}
```
