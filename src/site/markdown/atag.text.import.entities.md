# `atag.text.import.entities`

## Description

Import only the entity declarations of a TEI/XML document stored on a node property,
controlled by an [import profile](profiles.html). Nothing is extracted as text and nothing
is written to the start node: this is how a register that is declared once for a whole
corpus enters the graph, before the texts that refer to it are imported with
[atag.text.import.tei](atag.text.import.tei.html).

A declaration that already exists in the graph (by `entityKey`) is returned as it is;
the others are created - `createMissingEntities` defaults to `true` here.

## Parameters

| name         | type   | description                                     | default value |
|--------------|--------|-------------------------------------------------|---------------|
| startNode    | node   | node holding the document                       |               |
| propertyKey  | string | property name of the document                   |               |
| profile      | map    | import profile                                  | `{}`          |
|              |        |                                                 |               |
| return value | node   | the declared entities, existing or new          |               |

The profile keys that matter: `entityXPath` selects the declarations (the TEI default looks
for `<list type="entity">/<item>` in `<standOff>`), `entityLabelXPath` produces the display
name from a declaration, `entitySourceProperty` keeps the declaration verbatim, and the
model's entity labels are the labels of the new nodes. The declaring element is kept in
`tag`.

## Example

```cypher
MATCH (c:Corpus {uuid: 'sozinianer'})
CALL atag.text.import.entities(c, 'xml', {
  model: {entity: ['Entity', 'Person']},
  rootElement: 'teiCorpus',
  entityXPath: "/*[local-name()='teiCorpus']/*[local-name()='standOff']/*[local-name()='listPerson']/*[local-name()='person']",
  entityLabelXPath: "normalize-space((.//*[@type='reg'])[1])",
  entitySourceProperty: 'tei'
}) YIELD node
CREATE (node)-[:PART_OF]->(c)
RETURN count(node) AS persons
```

Linking the entities `PART_OF` the collection that declares them lets an export starting at
that collection reach the whole register, not only the entities some text refers to. The
[second worked example](worked-example-letter.html) imports a register of persons, places
and terms this way.
