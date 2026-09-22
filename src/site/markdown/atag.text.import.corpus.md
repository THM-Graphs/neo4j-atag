# `atag.text.import.corpus`

## Description

Import a TEI/XML document as the collections, texts, annotations and entities its
[profile](profiles.html) describes. Where
[atag.text.import.tei](atag.text.import.tei.html) reads one document into one content node
that already exists, this procedure builds the hierarchy first: it cuts the source into the
levels of the profile's `documents` section, links each level to the one it is part of,
imports the `registers` as entities, and runs the text of every content level through the
import.

That makes a corpus document - a `<teiCorpus>` of letters, each with its witnesses - one
call instead of hand-written Cypher.

## Parameters

| name         | type   | description                                  | default value |
|--------------|--------|----------------------------------------------|---------------|
| xml          | string | the source document                          |               |
| profile      | map    | import profile, or `{profile: 'name'}`       | `{}`          |
|              |        |                                              |               |
| return value | node   | the collection and content nodes created, outermost first |  |

## The `documents` section

Each entry selects its parts *within the fragment of the level above*, so the expression is
absolute in that fragment - the same way it would be if that fragment were handed to
[atag.text.xpath](atag.text.xpath.html).

| key              | description                                                        | default              |
|------------------|--------------------------------------------------------------------|----------------------|
| `xpath`          | selects the parts of this level                                    |                      |
| `concept`        | `collection` or `content`; content levels get the text import      | `content`            |
| `label`          | label of the new nodes; must be one the model knows for the concept | the model's primary |
| `id`             | identifier when the source has none, e.g. for the outermost level  | from `idAttribute`   |
| `properties`     | attributes to keep as properties                                   | all of them          |
| `sourceProperty` | property the fragment itself is kept on                            | not kept             |
| `idAttribute`, `headerXPath`, `headerProperty`, `rootElement` | override the profile's for this level |     |
| `textXPath`      | overrides the profile's `xpath` for this level's text              | the profile's        |

## The `registers` section

| key              | description                                             | default                      |
|------------------|---------------------------------------------------------|------------------------------|
| `xpath`          | selects the entity declarations                         |                              |
| `labels`         | labels added to the entities of this register           | -                            |
| `labelXPath`     | display name, relative to a declaration                 | the profile's `entityLabelXPath` |
| `sourceProperty` | property the declaration is kept on, verbatim           | the profile's `entitySourceProperty` |

Registers are imported before any text, so that the references in those texts have entities
to resolve against, and every entity is linked `PART_OF` the outermost document.
[atag.text.import.entities](atag.text.import.entities.html) stays the way to import a
register on its own.

## Example

```cypher
CALL atag.text.import.corpus($xml, {profile: 'sozinianer'}) YIELD node
RETURN labels(node), node.uuid
```

The [second worked example](worked-example-letter.html) takes a corpus of a real edition
through this procedure and exports it again.
