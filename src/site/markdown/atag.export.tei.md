# `atag.export.tei.fromNode` / `atag.export.tei.list`

## Description

Export a subgraph as TEI/XML. `fromNode` traverses from a start node according to the
[export profile](profiles.html), `list` takes the nodes and relationships the caller has
already selected.

An annotation is written inline wherever the XML hierarchy permits it. Annotations that
overlap another one, annotations on annotations, and every annotation at all when the
profile asks for `serialization: 'standoff'`, are written into `<standOff>` pointing back
at the text with a `string-range()` pointer. Collections become `<div>`, content nodes
become `<ab>`, and `REFERS_TO` relations become `@ref` pointers to entities declared in
`<standOff>`.

## Parameters

| name          | type   | description                                     | default value |
|---------------|--------|-------------------------------------------------|---------------|
| startNode     | node   | node to traverse from (`fromNode`)              |               |
| nodes         | list   | nodes to export (`list`)                        |               |
| relationships | list   | relationships to export (`list`)                |               |
| config        | map    | export profile                                  |               |
|               |        |                                                 |               |
| return value  | string | the TEI document, or a message if `fileName` is set |           |

## Example

```cypher
MATCH (t:Text {uuid: 'text-1'})
CALL atag.export.tei.fromNode(t, {
  dictionary: { elements: {persName: 'person-reference', s: 'sentence'} }
}) YIELD value
RETURN value
```

```xml
<ab xml:id="text-1"><s xml:id="a-2"><persName xml:id="a-1" ref="#hildegard">Hildegard</persName> writes to Berthold.</s></ab>
```

The result can be read back with [atag.text.import.tei](atag.text.import.tei.html). The
roundtrip is semantic: the text, the annotated ranges, their types, annotations on
annotations and entity references survive, while the serialization may differ in shape
from the document that was originally imported.
