# A worked example: a TEI letter in and out of the graph

This walkthrough takes one small TEI document through the whole pipeline: a project model
is declared, the document is imported, the resulting graph is queried, and the same text
is written back as TEI - once inline and once as stand-off markup. Every statement and
every result below is executed by `WorkedExampleTest`, so what you read here is what the
procedures produce.

The three decisions it demonstrates are recorded as architecture decision records in the
repository: `docs/adr/0001-profile-driven-import-export-architecture.md` (profile-driven
pipelines), `docs/adr/0002-tei-serialization-inline-and-standoff.md` (TEI inline and
stand-off) and `docs/adr/0003-project-model-as-meta-graph.md` (the project model as a
meta graph).

## The source document

A letter with two sentences, two person references, an entity list - and two verse lines
that deliberately do not respect the sentence boundaries: the first line ends in the
middle of the second sentence. That is the ordinary case in a digital edition, and the
reason the text is a graph rather than a tree.

```xml
<TEI xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader><fileDesc><titleStmt><title>Letter to Berthold</title></titleStmt></fileDesc></teiHeader>
  <text><body><ab xml:id="letter-1"><s xml:id="s-1"><persName xml:id="p-1" ref="#hildegard">Hildegard</persName> writes to <persName xml:id="p-2" ref="#berthold">Berthold</persName>.</s> <s xml:id="s-2">She sends greetings.</s></ab></body></text>
  <standOff>
    <listAnnotation>
      <annotation xml:id="l-1" target="#string-range(letter-1,0,33)" type="line"/>
      <annotation xml:id="l-2" target="#string-range(letter-1,34,16)" type="line"/>
      <annotation xml:id="c-1" target="#p-1" type="commentary" note="uncertain reading"/>
    </listAnnotation>
    <list type="entity">
      <item xml:id="hildegard" n="Hildegard von Bingen" type="Person" wikidataId="Q70991"/>
      <item xml:id="berthold" n="Berthold von Zwiefalten" type="Person"/>
    </list>
  </standOff>
</TEI>
```

Note that `<text>` is written without indentation: whitespace inside the text *is* text,
and it would end up in the character offsets the annotations are addressed by.

## 1. Declare the project model

This project calls its collections `Manuscript` and its content nodes `Transcript`, and
leaves annotations and entities at their default names. Written to the graph once, the
model is available to every later call as `model: 'meta'` instead of being repeated in
each profile:

```cypher
CALL atag.model.meta.write({
  model: {
    collection: ['Manuscript'],
    content:    ['Transcript'],
    annotation: ['Annotation'],
    entity:     ['Entity']
  }
}) YIELD value
RETURN value
```

See [atag.model.meta](atag.model.meta.html) for what the meta graph looks like.

## 2. Put the document into the graph

The TEI document is stored on a property of the content node it describes. The manuscript
it belongs to becomes a collection node:

```cypher
CREATE (m:Manuscript {uuid: 'ms-1', label: 'Cod. Sang. 963'})
CREATE (t:Transcript {uuid: 'letter-1', xml: $xml})
CREATE (t)-[:PART_OF]->(m)
```

For a document that lives elsewhere, [atag.text.load](atag.text.load.html) fetches it from
an HTTP or file URL, and [atag.text.xslt](atag.text.xslt.html) transforms it beforehand.

## 3. Import

```cypher
MATCH (t:Transcript {uuid: 'letter-1'})
CALL atag.text.import.tei(t, 'xml', {
  model: 'meta',
  dictionary: { elements: {persName: 'person-reference', s: 'sentence'} },
  createMissingEntities: true
}) YIELD node
RETURN count(node) AS annotations
```

```
annotations
7
```

Three profile keys do the work here, all described under
[profiles](profiles.html):

* `model: 'meta'` takes the labels from step 1, so the annotations are attached to the
  `Transcript` node and entities are looked up as `Entity` nodes.
* `dictionary.elements` translates markup names into the project's annotation types.
  Elements the dictionary does not know - here none - keep their name in `tag` and simply
  arrive without a type.
* `createMissingEntities` lets the `<list type="entity">` declarations create the two
  person nodes. Without it, only references to entities already in the graph are wired up.

Everything else is a TEI default of the procedure: the body is the text, `<standOff>`
carries annotations and entity declarations, `xml:id` is the identifier, and `@ref`
points at an entity.

## 4. What the import produced

The text every annotation is addressed against sits on the content node:

```cypher
MATCH (t:Transcript {uuid: 'letter-1'})
RETURN t.plainText AS plainText
```

```
plainText
"Hildegard writes to Berthold. She sends greetings."
```

The annotations are nodes over ranges of that string. The inline elements and the
stand-off annotations are indistinguishable afterwards - which is the whole point of
resolving `string-range()` pointers during the import:

```cypher
MATCH (t:Transcript {uuid: 'letter-1'})-[:HAS_ANNOTATION]->(a:Annotation)
RETURN a.uuid, a.type, a.tag, a.startIndex, a.endIndex,
       substring(t.plainText, a.startIndex, a.endIndex - a.startIndex) AS text
ORDER BY a.startIndex, a.endIndex DESC
```

| a.uuid | a.type            | a.tag      | a.startIndex | a.endIndex | text                                |
|--------|-------------------|------------|--------------|------------|-------------------------------------|
| l-1    | line              | `null`     | 0            | 33         | `Hildegard writes to Berthold. She` |
| s-1    | sentence          | `s`        | 0            | 29         | `Hildegard writes to Berthold.`     |
| p-1    | person-reference  | `persName` | 0            | 9          | `Hildegard`                         |
| p-2    | person-reference  | `persName` | 20           | 28         | `Berthold`                          |
| s-2    | sentence          | `s`        | 30           | 50         | `She sends greetings.`              |
| l-2    | line              | `null`     | 34           | 50         | `sends greetings.`                  |

`tag` keeps the element name an annotation was written as. The two lines have none: they
were written as the generic `<annotation>` of the stand-off vocabulary, which says where
an annotation was encoded, not what it is.

`@ref` became a relationship rather than a string property, so an entity is a node the
whole edition shares:

```cypher
MATCH (:Transcript {uuid: 'letter-1'})-[:HAS_ANNOTATION]->(a:Annotation)-[:REFERS_TO]->(e:Entity)
RETURN a.uuid, e.uuid, e.label, e.wikidataId
ORDER BY a.uuid
```

| a.uuid | e.uuid    | e.label                 | e.wikidataId |
|--------|-----------|-------------------------|--------------|
| p-1    | hildegard | Hildegard von Bingen    | Q70991       |
| p-2    | berthold  | Berthold von Zwiefalten | `null`       |

The `type` of an entity declaration became a label, so `hildegard` is an `:Entity:Person`.

An annotation whose target is another annotation hangs off that annotation instead of the
text:

```cypher
MATCH (:Transcript {uuid: 'letter-1'})-[:HAS_ANNOTATION]->(a:Annotation)
MATCH (a)-[:HAS_ANNOTATION]->(c:Annotation)
RETURN a.uuid, c.uuid, c.type, c.note
```

| a.uuid | c.uuid | c.type     | c.note            |
|--------|--------|------------|-------------------|
| p-1    | c-1    | commentary | uncertain reading |

## 5. Export as TEI, inline where possible

The export procedures need `dbms.security.procedures.unrestricted` to be set, see
[installation](installation.html); steps 1 to 4 run without it.

```cypher
MATCH (t:Transcript {uuid: 'letter-1'})
CALL atag.export.tei.fromNode(t, {
  model: 'meta',
  dictionary: { elements: {persName: 'person-reference', s: 'sentence'} },
  ignoreProperties: ['xml']
}) YIELD value
RETURN value
```

`ignoreProperties` keeps the source document, which is still sitting on the `xml` property
of the transcript, out of the export. The dictionary is the same one the import used, read
in the other direction: an annotation type becomes an element name again.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<TEI xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader><fileDesc><titleStmt><title>ATAG export</title></titleStmt>
    <publicationStmt><p>exported from a Neo4j property graph by neo4j-atag</p></publicationStmt>
    <sourceDesc><p>born-digital graph data</p></sourceDesc></fileDesc></teiHeader>
  <text><body><ab xml:id="letter-1"><seg xml:id="l-1" type="line"><s xml:id="s-1"><persName xml:id="p-1" ref="#hildegard">Hildegard</persName> writes to <persName xml:id="p-2" ref="#berthold">Berthold</persName>.</s> She</seg> <seg xml:id="l-2" type="line">sends greetings.</seg></ab></body></text>
  <standOff>
    <listAnnotation>
      <annotation target="#string-range(letter-1,30,20)" xml:id="s-2" type="sentence"/>
      <annotation target="#p-1" xml:id="c-1" note="uncertain reading" type="commentary"/>
    </listAnnotation>
    <list type="entity">
      <item xml:id="berthold" n="Berthold von Zwiefalten" type="Entity,Person"/>
      <item xml:id="hildegard" n="Hildegard von Bingen" type="Entity,Person" wikidataId="Q70991"/>
    </list>
  </standOff>
</TEI>
```

(Line breaks were added outside `<text>` for readability. The exporter emits none inside
it, for the reason given above.)

Four things happened here:

* The lines and the first sentence nest cleanly, so they are written inline. Because the
  dictionary has no element for `line`, the lines become the neutral `<seg type="line">`
  rather than being dropped or invented as `<line>`.
* The second sentence overlaps the first line - `l-1` ends inside `s-2` - so it cannot be
  an element. It moves into `<standOff>` and points back at the same characters with
  `string-range(letter-1,30,20)`, that is offset 30, length 20.
* The commentary, an annotation on an annotation, has no range of its own and targets
  `#p-1`.
* `REFERS_TO` became `@ref` again, and the two entities are declared in `<standOff>`.

## 6. Export as pure stand-off markup

The same graph, with `serialization: 'standoff'` added to the profile, keeps the text
completely unmarked and puts every annotation into `<standOff>`. This is the serialization
to use when consumers should not have to deal with a hierarchy that was chosen for them:

```xml
<text><body><ab xml:id="letter-1">Hildegard writes to Berthold. She sends greetings.</ab></body></text>
<standOff>
  <listAnnotation>
    <annotation target="#string-range(letter-1,0,33)" xml:id="l-1" type="line"/>
    <annotation target="#string-range(letter-1,0,29)" xml:id="s-1" type="sentence"/>
    <annotation target="#string-range(letter-1,0,9)" xml:id="p-1" ref="#hildegard" type="person-reference"/>
    <annotation target="#p-1" xml:id="c-1" note="uncertain reading" type="commentary"/>
    <annotation target="#string-range(letter-1,20,8)" xml:id="p-2" ref="#berthold" type="person-reference"/>
    <annotation target="#string-range(letter-1,30,20)" xml:id="s-2" type="sentence"/>
    <annotation target="#string-range(letter-1,34,16)" xml:id="l-2" type="line"/>
  </listAnnotation>
  ...
</standOff>
```

## 7. Export a whole collection

Starting the traversal at the manuscript instead of one of its transcripts includes the
collection, which becomes a `<div>` around the texts that are `PART_OF` it - and gives the
document its title:

```cypher
MATCH (m:Manuscript {uuid: 'ms-1'})
CALL atag.export.tei.fromNode(m, $profile) YIELD value
RETURN value
```

```xml
<titleStmt><title>Cod. Sang. 963</title></titleStmt>
...
<text><body><div xml:id="ms-1" label="Cod. Sang. 963"><ab xml:id="letter-1">...</ab></div></body></text>
```

Adding `fileName: 'letter-1.xml'` to the profile writes the result into Neo4j's import
directory instead of returning it.

## 8. Reading the export back

The export of step 5 is a TEI document like any other, so it can be imported with the same
profile:

```cypher
CREATE (t:Transcript {uuid: 'letter-2', xml: $exported})
```

```cypher
MATCH (t:Transcript {uuid: 'letter-2'})
CALL atag.text.import.tei(t, 'xml', $profile) YIELD node
RETURN count(node) AS annotations
```

The result is the graph of step 4 again: the same plain text, and the same six ranges with
the same types - even though `s-2` travelled as stand-off markup this time while `s-1`
travelled as an element.

The roundtrip is semantic, not lexical. What survives is the text, the annotated ranges,
their types and properties, annotations on annotations, and the identity of the entities
referred to. What is not promised is the exact document: which annotation is written
inline and which one as stand-off follows from the graph, not from the shape of whatever
document the graph was built from.
