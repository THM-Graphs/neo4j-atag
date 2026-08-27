# Import and export profiles

Import and export are model-guided transformations, not generic file conversions. Both
directions run as pipelines whose phases are controlled by a *profile*, given as the
config map of the procedure call.

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

The defaults above are those of [atag.text.import.tei](atag.text.import.tei.html);
`atag.text.import.html` and `atag.text.import.xml` build their profile from their
positional arguments.

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
| `fileName`              | write into the import directory instead of returning the result   | -                                         |
