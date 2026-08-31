# `atag.text.import.tei`

## Description

Import a TEI/XML document stored on a node property, controlled by an
[import profile](profiles.html). In contrast to `atag.text.import.xml`, this procedure
runs the full pipeline: it validates the document element, resolves stand-off annotations
and entity declarations, maps element and attribute names with the profile's dictionary,
and turns entity references into relationships.

## Parameters

| name         | type   | description                             | default value |
|--------------|--------|-----------------------------------------|---------------|
| startNode    | node   | content node holding the TEI document   |               |
| propertyKey  | string | property name of the TEI document       |               |
| profile      | map    | import profile                          | `{}`          |
|              |        |                                         |               |
| return value | node   | new annotation nodes                    |               |

## Stand-off markup

An annotation in `<standOff>` addresses a range of the text with the XPointer scheme
`string-range(id, start, length)` and an annotation on an annotation with a plain `#id`
pointer:

```xml
<standOff>
  <listAnnotation>
    <annotation target="#string-range(text-1,5,15)" xml:id="a-3" type="phrase"/>
    <annotation target="#a-1" xml:id="c-1" type="commentary"/>
  </listAnnotation>
  <list type="entity">
    <item xml:id="hildegard" n="Hildegard von Bingen" type="Person"/>
  </list>
</standOff>
```

Both encodings result in the same kind of annotation node, which is what makes an inline
and a stand-off encoding of the same relation interchangeable.

## Example

```cypher
MATCH (t:Text {uuid: 'text-1'})
CALL atag.text.import.tei(t, 'xml', {
  dictionary: { elements: {persName: 'person-reference', s: 'sentence'} },
  createMissingEntities: true
}) YIELD node
RETURN node
```

The [worked example](worked-example.html) walks through this import step by step and shows
the graph it produces.
