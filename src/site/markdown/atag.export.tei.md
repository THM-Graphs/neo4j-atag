# `atag.export.tei.fromNode` / `atag.export.tei.list`

## Description

Export a subgraph as TEI/XML. `fromNode` traverses from a start node according to the
[export profile](profiles.html), `list` takes the nodes and relationships the caller has
already selected.

Requires `dbms.security.procedures.unrestricted` to cover `atag.export.*`, see
[installation](installation.html).

An annotation is written inline wherever the XML hierarchy permits it. Annotations that
overlap another one, annotations on annotations, and every annotation at all when the
profile asks for `serialization: 'standoff'`, are written into `<standOff>` pointing back
at the text with a `string-range()` pointer. Collections become `<div>`, content nodes
become `<ab>`, and `REFERS_TO` relations become `@ref` pointers (or whatever
`referenceAttribute` names) to entities declared in `<standOff>`.

A node that carries a header (`headerProperty`) is written as a `<TEI>` of its own, with
its identifier and properties as attributes and the header verbatim, nested into the
`<TEI>` of its parent - a corpus comes back as a corpus. A root without a header gets a
placeholder header. Entities whose declaration was kept verbatim (`entitySourceProperty`)
are written back into the TEI list their element belongs to, `<listPerson>` for
`<person>` and so on; the others are declared as `<list type="entity"><item/>`.

Annotations over the same characters are nested by the `depth` the import recorded, and a
pointer the import could not resolve is written back as the attribute it was kept in. An
`xml:id` is written where the node has an identifier or where a pointer needs one - a
generated `atag-N` appears only in the second case.

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

The [worked example](worked-example.html) shows both serializations of one text, including
an annotation that has to move into `<standOff>` because it overlaps another one.
