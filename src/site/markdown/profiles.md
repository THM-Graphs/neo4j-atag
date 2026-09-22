# Import and export profiles

Import and export are model-guided transformations, not generic file conversions. Both
directions run as pipelines whose phases are controlled by a *profile*, given as the
config map of the procedure call.

The [worked example](worked-example.html) shows the keys below in use on a complete
import and export; the [second worked example](worked-example-letter.html) uses the keys
for headers, registers and references on a document from a real edition.

## A profile is one artifact

A profile can be written as the config map of a single call, or stored once under a name
with [atag.profile](atag.profile.html) and referred to afterwards:

```cypher
CALL atag.profile.write({
  name: 'sozinianer',
  model: {collection: ['Corpus', 'Letter'], content: ['Witness']},
  documents: [...], registers: [...],
  import: {referenceAttributes: ['corresp']},
  export: {referenceAttribute: 'corresp'}
})
```

```cypher
CALL atag.text.import.corpus($xml, {profile: 'sozinianer'}) YIELD node RETURN node
```

The sections `import` and `export` hold the keys that mean something in one direction
only; everything else applies to both. A call that names a profile may still override
single keys - what the call says wins over what the profile says.

| section     | what it holds                                                      |
|-------------|--------------------------------------------------------------------|
| `model`     | the project model, also written to the meta graph                  |
| `dictionary`| element and attribute names of the project's markup                |
| `documents` | the levels of the document hierarchy, see [atag.text.import.corpus](atag.text.import.corpus.html) |
| `registers` | where the entity declarations are, and which labels they carry     |
| `import`    | keys of the import direction                                       |
| `export`    | keys of the export direction                                       |

XPath in a profile may use the `*:name` wildcard for "this element in any namespace"
(`/*:TEI/*:text/*:body`) or the `*[local-name()='name']` form of XPath 1.0. They mean the
same thing: the document structure is selected with an engine that knows the wildcard, and
the expressions handed to the older engine of the text phases are rewritten.

## Pipelines

| direction | phases                                                                                              |
|-----------|-----------------------------------------------------------------------------------------------------|
| import    | 1. parsing + validation &rarr; 2. extracting structure &rarr; 3. dictionary mapping &rarr; 4. graph construction |
| export    | 1. graph traversal &rarr; 2. dictionary mapping &rarr; serialization (JGF, stand-off JSON/XML, TEI)   |

## The project model

The generic vocabulary is the RAMEN meta-model: the concepts *Collection*, *Content*,
*Entity* and *Annotation*, connected by `PART_OF`, `HAS_ANNOTATION` and `REFERS_TO`. A
project model refines the concepts with its own labels:

```cypher
{
  model: {
    collection: ['Collection'],
    content:    ['Text'],
    entity:     ['Entity'],
    annotation: ['Annotation'],
    partOf: 'PART_OF', hasAnnotation: 'HAS_ANNOTATION', refersTo: 'REFERS_TO'
  }
}
```

`model: 'meta'` reads the model from the meta graph instead, see
[atag.model.meta](atag.model.meta.html).

## The dictionary

The dictionary translates between a markup vocabulary and the graph vocabulary, in both
directions:

```cypher
{
  dictionary: {
    elements:   { persName: 'person-reference', s: 'sentence' },  // element name <-> annotation type
    attributes: { ref: 'reference' },                             // attribute name <-> property key
    attributePrefix: '',        // prefix for attributes the dictionary does not know
    elementProperty: 'tag',     // property keeping the original element name
    typeProperty: 'type',       // property holding the annotation type
    defaultElement: 'seg'       // element for an annotation without a mapping
  }
}
```

Names the dictionary does not know are passed through rather than dropped: the element
name is always kept in `tag`, unmapped attributes become properties.

## What is not modelled travels verbatim

A source document contains things the model has no concept for - a `<teiHeader>`, the
`<birth>` and `<idno>` children of an entity declaration. The import keeps them as XML
strings on the node they belong to (`headerProperty`, `entitySourceProperty`), and the TEI
export writes them back as they were. In the same spirit, a reference the graph cannot
resolve stays a property named after its attribute instead of being dropped, and the
nesting depth of an element is kept in `depth` so that two annotations over the same
characters can be written back in their original order.

## Import profile keys

| key                     | description                                                       | default        |
|-------------------------|-------------------------------------------------------------------|----------------|
| `xpath`                 | selects the nodes that make up the text                           | TEI body       |
| `standoffXPath`         | selects stand-off annotations; empty disables resolution          | TEI `standOff` |
| `entityXPath`           | selects entity declarations                                       | TEI `standOff` |
| `rootElement`           | expected document element, checked in phase 1                     | `TEI`          |
| `idAttribute`           | attribute holding an identifier                                   | `xml:id`       |
| `idProperty`            | property the identifier is written to                             | `uuid`         |
| `referenceAttributes`   | attributes pointing at an entity                                  | `['ref']`      |
| `annotationLabel`       | label of new annotation nodes                                     | model default  |
| `plainTextProperty`     | property receiving the extracted plain text                       | `plainText`    |
| `relationshipType`      | relationship from the content node to its annotations             | model default  |
| `addUuid`               | generate an identifier where the source has none                  | `true`         |
| `entityKey`             | property an entity reference is resolved against                  | `uuid`         |
| `createMissingEntities` | create entities that are declared but not yet in the graph        | `false`        |
| `headerXPath`           | selects the document header, kept verbatim; empty keeps none      | TEI `teiHeader` |
| `headerProperty`        | property of the content node the header is stored on              | `teiHeader`    |
| `entityLabelXPath`      | evaluated relative to a declaration to obtain its display name    | `@n`           |
| `entitySourceProperty`  | property an entity declaration is stored on verbatim              | none           |
| `entityLabels`          | labels added to the entities this import declares                 | `[]`           |

The defaults above are those of [atag.text.import.tei](atag.text.import.tei.html) and
[atag.text.import.entities](atag.text.import.entities.html); `atag.text.import.html` and
`atag.text.import.xml` build their profile from their positional arguments.

## Export profile keys

| key                     | description                                                       | default                                   |
|-------------------------|-------------------------------------------------------------------|-------------------------------------------|
| `followIncoming`        | relationship types followed against their direction               | `['PART_OF']`                             |
| `followOutgoing`        | relationship types followed in their direction                    | `['HAS_ANNOTATION', 'NEXT_TOKEN', 'REFERS_TO']` |
| `includeCharacterChain` | also follow the character and token chain                         | `true`                                    |
| `annotationTypes`       | allow-list of annotation types                                    | all                                       |
| `textProperties`        | properties holding the character content of a content node        | `['text', 'plainText']`                   |
| `ignoreProperties`      | properties that should not be serialized at all                   | `[]`                                      |
| `serialization`         | `inline` or `standoff`, for TEI output                            | `inline`                                  |
| `entityKey`             | property an entity reference points at                            | `uuid`                                    |
| `idProperty`            | property a node is addressed by in the serialization              | `uuid`                                    |
| `referenceAttribute`    | attribute an entity reference is written as, for TEI output       | `ref`                                     |
| `headerProperty`        | property holding a verbatim header; a node that has one becomes a `<TEI>` of its own | `teiHeader`    |
| `entitySourceProperty`  | property holding a verbatim entity declaration, written back as it is | none                                  |
| `fileName`              | write into the import directory instead of returning the result   | -                                         |
