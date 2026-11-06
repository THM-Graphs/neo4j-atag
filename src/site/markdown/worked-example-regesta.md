# A worked example III: a sample of a register, exported and imported as JGF

The [first worked example](worked-example.html) turns a letter into a graph and back to
TEI; the [second](worked-example-letter.html) checks that roundtrip element by element.
This one starts from a graph that already exists: a register of regests with a
hierarchy of departments and volumes. It takes **one regest per department**, exports each
as a JGF file, imports the files into an empty database, and compares the result with the
original. Every statement and every result below is executed by `RegestaExampleTest`, so
what you read here is what the procedures produce.

The graph is the one of the *Regesta Imperii*: a regest is a `Collection` whose texts
(summary, title, identifier, place of issue) are `Content` nodes, annotated with `Annotation`
nodes. The example uses a small graph of the same shape, with invented texts. Run against
the real database, the same statements wrote 14 files of 12 to 216 KB, one per department.

## The graph

```text
(:Department)<-[:PART_OF]-(:Volume)<-[:PART_OF]-(:Regesta)<-[:PART_OF]-(:Content:Text:Summary)
                                         |                                       |
                                         |-[:HAS_ANNOTATION]->(:Annotation)      |-[:HAS_ANNOTATION]->(:Annotation:Next)-[:REFERS_TO]->(:Content)
                                         |                          |-[:REFERS_TO]->(:Regesta | :Entity)
(:Entity:Person)-[:HAS_ANNOTATION]->(:Annotation)-[:REFERS_TO]->(:Content)
```

* A regest has texts, and annotations of its own (the place of issue, a `Next` link to the
  following regest).
* The texts of a regest are chained by `Next` annotations.
* Persons and places are entities. They own the annotations that mark a mention, and those
  annotations point *at* the text.
* The properties are typed: strings, integers, booleans, string arrays, and a `Point`
  (`nLatLong`) on the regests.

The statement that builds the sample:

```cypher
CREATE (d1:Collection:Department {uuid: 'dep-1', label: 'RI01 - Karolinger', type: 'department'})
CREATE (v1:Collection:Volume {uuid: 'vol-1', label: '1 - Karolinger 751-918', type: 'volume'})-[:PART_OF]->(d1)
CREATE (r1:Collection:Regesta {uuid: 'reg-1', label: 'Karl III - RI I n. 1707 885 iuni 21, Stirpiaco villa',
        sorting: '001_0000001707', identifier: 'RI I n. 1707', isoStartDate: '0885-06-21',
        laufendenummer: 3856, latitude: '48.76667', hasImage: true, volume: ['1 - Karolinger 751-918'],
        nLatLong: point({latitude: 48.76667, longitude: 4.81667})})-[:PART_OF]->(v1)
CREATE (r2:Collection:Regesta {uuid: 'reg-2', label: 'Karl III - RI I n. 1708 885 iulii 2',
        sorting: '001_0000001708', identifier: 'RI I n. 1708', isoStartDate: '0885-07-02'})-[:PART_OF]->(v1)
CREATE (s1:Content:Text:Summary {uuid: 'txt-1', label: 'Summary',
        text: 'Karl III bestätigt dem Kloster Lorsch seinen Besitz.',
        htmlText: '<p>Karl III bestätigt dem Kloster Lorsch seinen Besitz.</p>'})-[:PART_OF]->(r1)
CREATE (i1:Content:Text:Identifier {uuid: 'txt-2', label: 'Identifier', text: 'RI I n. 1707'})-[:PART_OF]->(r1)
CREATE (t2:Content:Text:Title {uuid: 'txt-3', label: 'Title', text: 'Karl III'})-[:PART_OF]->(r2)
CREATE (i1)-[:HAS_ANNOTATION]->(:Annotation:Next {uuid: 'ann-1', label: 'next', type: 'next'})-[:REFERS_TO]->(s1)
CREATE (r1)-[:HAS_ANNOTATION]->(:Annotation:Next {uuid: 'ann-2', label: 'next', type: 'next'})-[:REFERS_TO]->(r2)
CREATE (pl:Entity:Place {uuid: 'ent-1', label: 'Étrépy'})
CREATE (r1)-[:HAS_ANNOTATION]->(:Annotation:RegestaAnnotation {uuid: 'ann-3', label: 'placeOfIssue',
        type: 'ri:placeOfIssue', original: 'Stirpiaco villa'})-[:REFERS_TO]->(pl)
CREATE (pe:Entity:Person {uuid: 'ent-2', label: 'Karl III'})
CREATE (pe)-[:HAS_ANNOTATION]->(:Annotation {uuid: 'ann-4', type: 'mention', startIndex: 0, endIndex: 8})-[:REFERS_TO]->(s1)
CREATE (d2:Collection:Department {uuid: 'dep-2', label: 'RI02 - Sächsisches Haus', type: 'department'})
CREATE (v2:Collection:Volume {uuid: 'vol-2', label: '2 - Heinrich I.', type: 'volume'})-[:PART_OF]->(d2)
CREATE (r3:Collection:Regesta {uuid: 'reg-3', label: 'Heinrich I. - RI II n. 1', sorting: '002_0000000001',
        identifier: 'RI II n. 1', isoStartDate: '0919-05-24', laufendenummer: 1,
        nLatLong: point({latitude: 50.0, longitude: 10.0})})-[:PART_OF]->(v2)
CREATE (:Content:Text:Summary {uuid: 'txt-4', label: 'Summary', text: 'Heinrich I. wird zum König gewählt.'})-[:PART_OF]->(r3)
CREATE (r3)-[:HAS_ANNOTATION]->(:Annotation:RegestaAnnotation {uuid: 'ann-5', label: 'placeOfIssue',
        type: 'ri:placeOfIssue', original: 'Fritzlar'})-[:REFERS_TO]->(pl)
```

Two departments, three regests, 18 nodes with a `uuid`. The two regests of the first
department are neighbours (`reg-1` has a `Next` annotation that refers to `reg-2`), and
both departments mention the same place, `ent-1`.

## 1. The profile

The shape of the register is declared once and stored under a name with
[atag.profile.write](atag.profile.html): the project model, which concept each label
stands for, and the scope an export traverses.

```cypher
CALL atag.profile.write({
  name: 'regesta',
  model: {
    collection: ['Collection'],
    content:    ['Content'],
    annotation: ['Annotation'],
    entity:     ['Entity']
  },
  export: {
    followIncoming: ['PART_OF'],
    followOutgoing: ['HAS_ANNOTATION', 'REFERS_TO'],
    includeCharacterChain: false
  }
}) YIELD value
RETURN value
```

Afterwards a call refers to it as `{profile: 'regesta'}`. For JGF the profile does less than
for TEI: JGF serializes the graph as it is, with every label and every property, so no
dictionary and no property filter applies. What it contributes is the model, the
`fileName` of an export, and the scope of a traversal - which is the next section.

## 2. Why this sample is collected explicitly

[atag.export.jgf.fromNode](atag.export.jgf.fromNode.html) starts at a node and follows the
relationship types of the profile. Started at the first regest:

```cypher
MATCH (r:Regesta {uuid: 'reg-1'})
CALL atag.export.jgf.fromNode(r, {profile: 'regesta'}) YIELD value
UNWIND keys(value.graph.nodes) AS k
RETURN value.graph.nodes[k].metadata.uuid AS uuid ORDER BY uuid
```

it follows `REFERS_TO` into the neighbouring regest and brings its texts (`txt-3`) along;
a register where regests refer to regests leads, step by step, to the whole database. And
because `PART_OF` is followed against its direction only, the volume and the department
are missing, so the sample could not be attached to its place in the hierarchy when it is
imported. A sample of one regest per department therefore is collected with a query, and
the nodes and relationships are handed to [atag.export.jgf.list](atag.export.jgf.html).

## 3. Collecting one regest per department

```cypher
MATCH (d:Department)
CALL (d) {
  MATCH (d)<-[pv:PART_OF]-(v:Volume)<-[pr:PART_OF]-(r:Regesta)
  RETURN v, r, pv, pr ORDER BY r.sorting LIMIT 1
}
CALL (r) {
  OPTIONAL MATCH (c:Content)-[pc:PART_OF]->(r)
  RETURN collect(c) AS contents, collect(pc) AS contentRels
}
WITH d, v, r, pv, pr, contents, contentRels, [r] + contents AS texts
CALL (texts) {
  UNWIND texts AS t
  OPTIONAL MATCH (t)-[h:HAS_ANNOTATION]->(a:Annotation)
  RETURN collect(DISTINCT a) AS ownAnn, collect(DISTINCT h) AS ownRels
}
CALL (texts) {
  UNWIND texts AS t
  OPTIONAL MATCH (e:Entity)-[h:HAS_ANNOTATION]->(a:Annotation)-[:REFERS_TO]->(t)
  RETURN collect(DISTINCT e) AS ents, collect(DISTINCT a) AS entAnn, collect(DISTINCT h) AS entRels
}
WITH d, v, r, pv, pr, contents, contentRels, ownAnn, ownRels, ents, entAnn, entRels,
     ownAnn + entAnn AS anns
CALL (anns) {
  UNWIND anns AS a
  OPTIONAL MATCH (a)-[rt:REFERS_TO]->(t)
  RETURN collect(DISTINCT t) AS targets, collect(DISTINCT rt) AS referRels
}
WITH d, r,
     [x IN [d, v, r] + contents + ownAnn + entAnn + ents + targets | x] AS allNodes,
     [x IN [pv, pr] + contentRels + ownRels + entRels + referRels | x] AS allRels
CALL (allNodes) { UNWIND allNodes AS n RETURN collect(DISTINCT n) AS nodes }
CALL (allRels) { UNWIND allRels AS e RETURN collect(DISTINCT e) AS rels }
```

The regest of a department is the one with the lowest `sorting`, so the sample is
reproducible. What belongs to it:

* its volume and department, to attach it again;
* its texts;
* the annotations of the regest and of its texts, and those that entities own and that
  refer to one of its texts;
* whatever those annotations refer to: entities, texts, and other regests.

Other regests are kept as *stubs*: the node with its properties, but none of its own
relationships and none of its texts. A stub carries the `uuid` that a later import
matches on.

```text
dep-1 | reg-1 | 12 | 12
dep-2 | reg-3 | 6 | 5
```

The columns are the department, the regest, and the number of nodes and relationships of
the sample.

## 4. Export

The export procedures need `dbms.security.procedures.unrestricted` to be set, see
[installation](installation.html); everything up to here runs without it.

The collecting statement continues with the export. `fileName` makes
[atag.export.jgf.list](atag.export.jgf.html) write into the import directory instead of
returning the document; the department's `uuid` is part of the name, because the labels of
departments contain characters a file name must not have.

```cypher
CALL atag.export.jgf.list(nodes, rels, {profile: 'regesta', fileName: 'regest_' + d.uuid + '.json'})
YIELD value
RETURN d.uuid AS department, r.uuid AS regest, size(nodes) AS nodes, size(rels) AS rels, value
```

Two files, `regest_dep-1.json` and `regest_dep-2.json`. Each is a JGF document: every node
under its element id, its labels joined by commas, its properties as `metadata`; every
relationship with its type and, if it has them, its properties. Property values keep their
type as far as JSON can say it: a `Point` is written as
`{"type": "Point", "srid": 4326, "coordinates": [4.81667, 48.76667]}`, a numeric array as a
JSON array (`docs/adr/0008-typed-property-values-in-jgf.md`).

## 5. Import into an empty database

The import matches nodes by a property and, if a node with that property and one of the
given labels exists, reuses it instead of creating another.

```cypher
CALL atag.import.jgfFile('regest_dep-1.json', {
  propertyKey: 'uuid',
  labels: ['Department', 'Volume', 'Regesta', 'Content', 'Annotation', 'Entity'],
  overwrite: false
}) YIELD nodeCount, relationshipCount
RETURN *
```

```text
regest_dep-1.json | 12 | 12
regest_dep-2.json | 5 | 5
```

The second file contains six nodes and creates five: the place `ent-1` is in both files and
is the same node after the import. Running both imports again changes nothing, `0 | 0`
twice, because every node is found by its `uuid` and a relationship between two nodes is
created once.

The list of labels decides what is matched. A node without one of them is created anew with
every import - for this register that would be the annotations, which is why `Annotation`
is on the list.

## 6. What proves the roundtrip

`RegestaExampleTest` takes a snapshot of the graph before the export - every node with a
`uuid`, its labels and its typed property values; every relationship as
`start -TYPE-> end` - and another after the import, and compares them:

* **Nodes.** 17 of the 18 nodes are there. The missing one is `txt-3`, the text of the
  neighbouring regest, which was not part of the sample.
* **Relationships.** 17 of 19. The two missing are the ones the stub `reg-2` had: its
  `PART_OF` to the volume and the `PART_OF` of its text.
* **Labels.** The same label sets on every node that came across.
* **Properties.** Identical, typed: the `Point`, the string array, the boolean, the integers,
  the strings. The one exception is below.

## 7. What changed, and what did not survive

* **A string that looks like an ISO date becomes a date.** `isoStartDate: '0885-06-21'`
  goes in as a string and comes back as a `date`. JSON has no date type, and the import
  reads every string that parses as `yyyy-MM-dd` as one. In the register this affects every
  property of that shape (`isoStartDate`, `isoEndDate`, `startDate`, `endDate`). If the
  target should keep them as strings, convert them after the import with
  `SET r.isoStartDate = toString(r.isoStartDate)`.
* **Stubs stay stubs.** A regest that is only referred to has its properties but not its
  place in the hierarchy. Importing the sample of its own department later completes it:
  the import matches it by `uuid`.
* **Identity.** The element ids of the JGF document are only keys inside the file; the
  imported nodes get new internal ids. The `uuid` is the identity of a node across
  databases.
* **A sample is not a copy.** Only what is reachable from the chosen regest is exported.
  Everything else that the entities of the sample are connected to - their other
  mentions, other regests - stays behind.
* **Integers.** Cypher sees the same `INTEGER` before and after; in the Java API the
  imported small values are `Integer` instead of `Long`.
