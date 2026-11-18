# `atag.profile.write` / `atag.profile.read` / `atag.profile.parse`

## Description

Keep an [import and export profile](profiles.html) in the graph under a name, so that a
project declares once how its documents are read and written and every later call refers
to that declaration instead of repeating it.

`atag.profile.write` stores a profile and, when it has a `model` section, writes that model
to the meta graph as [atag.model.meta.write](atag.model.meta.html) does - one call, both
artifacts. A profile is stored as one `(:Meta:Profile {name, json})` node: unlike the
model, a profile nests, and a Neo4j property cannot.

`atag.profile.parse` turns a profile written as JSON into the map the other two take, so a
profile kept in version control can be loaded with
[atag.text.load](atag.text.load.html) and stored unchanged.

## Parameters

| name         | type   | description                                     | default value |
|--------------|--------|-------------------------------------------------|---------------|
| profile      | map    | the profile, including its `name` (`write`)     |               |
| name         | string | name of a stored profile (`read`)               |               |
| json         | string | a profile written as JSON (`parse`)             |               |
|              |        |                                                 |               |
| return value | map    | what was written, the profile, or the parsed map |              |

## Example

```cypher
CALL atag.profile.write({
  name: 'sozinianer',
  model: {collection: ['Corpus', 'Letter'], content: ['Witness']},
  documents: [
    {xpath: '/*:teiCorpus',       concept: 'collection', label: 'Corpus', id: 'sozinianer'},
    {xpath: '/*:teiCorpus/*:TEI', concept: 'collection', label: 'Letter'},
    {xpath: '/*:TEI/*:TEI',       concept: 'content',    label: 'Witness'}
  ],
  import: {rootElement: 'teiCorpus', referenceAttributes: ['corresp']},
  export: {referenceAttribute: 'corresp'}
}) YIELD value
RETURN value
```

Every call then names the profile, and may still override single keys:

```cypher
CALL atag.text.import.corpus($xml, {profile: 'sozinianer'}) YIELD node RETURN node
```

```cypher
MATCH (c:Corpus {uuid: 'sozinianer'})
CALL atag.export.tei.fromNode(c, {profile: 'sozinianer', serialization: 'standoff'}) YIELD value
RETURN value
```

From a file:

```cypher
CALL atag.profile.write(atag.profile.parse(atag.text.load('file:///srv/edition/sozinianer.json')))
```

The [second worked example](worked-example-letter.html) drives a whole corpus from one
stored profile.
