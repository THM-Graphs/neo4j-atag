# Import/Export for Regesta Imperii

How to export **a single regest** of the *Regesta Imperii* database as a JGF file and
import it into another database. The graph has the shape described in
[worked example III](worked-example-regesta.html); this page covers the statements to run
against the real database.

## Export

Start from the regest, collect its subgraph and write it to the import directory:

```cypher
MATCH (n:Regesta) WHERE n.identifier = 'RI III,2,3 n. 1487'
CALL apoc.path.subgraphAll(n, {relationshipFilter: 'HAS_ANNOTATION>|<PART_OF'}) YIELD nodes, relationships
CALL atag.export.jgf.list(nodes, relationships, {fileName: 'regesta-1487.json'}) YIELD value
RETURN value
```

`value` is the message `31451 bytes written to regesta-1487.json`. The file is written to
the import directory of the database (`docker/import/` in the Docker setup), see
[installation](installation.html).

### Why not `atag.export.jgf.fromNode`

[`atag.export.jgf.fromNode`](atag.export.jgf.fromNode.html) follows `REFERS_TO` without a
restriction. In the register, the `next` annotation of every regest refers to the following
regest, so the traversal walks the whole chain: starting at `RI III,2,3 n. 1487` it
returns 76,907 nodes including 1,626 regests. Text links (annotations of type `a`) lead to
other regests in the same way.

The `relationshipFilter` above restricts the traversal by relationship type and direction:

| Relationship | Direction | Reaches |
|--------------|-----------|---------|
| `PART_OF` | incoming (`<`) | the texts (`Content`) of the regest |
| `HAS_ANNOTATION` | outgoing (`>`) | the annotations of the regest and of its texts |

`REFERS_TO` is not traversed. The export of `n. 1487` therefore holds 35 nodes and 40
relationships (27 `HAS_ANNOTATION`, 7 `PART_OF`, 6 `REFERS_TO`) and exactly one node
labelled `Regesta`. The `REFERS_TO` relationships in the file connect nodes inside this
regest only.

What the filter leaves out:

* the place of issue (`ri:placeOfIssue`): its `Entity:Place` node is reachable only through
  `REFERS_TO`
* the link to the following regest and to the regests cited in the text

To keep the entities as end points, add `REFERS_TO>` to the filter and stop the traversal
at them with a `labelFilter`; check the result for further regests before using it.

### Check the file

```python
import json
graph = json.load(open('regesta-1487.json'))['graph']
regests = [n for n in graph['nodes'].values() if n['label'] == 'Collection,Regesta']
assert len(regests) == 1
```

The label `Annotation,RegestaAnnotation` also contains the word *Regesta*, but is not a regest.

## Import

```cypher
CALL atag.import.jgfFile('regesta-1487.json')
YIELD nodeCount, relationshipCount
```

The file is read from the import directory. The procedure accepts the merge configuration
of [`atag.import.jgf`](atag.import.jgf.html) as a second argument:

```cypher
CALL atag.import.jgfFile('regesta-1487.json', {propertyKey: 'uuid', labels: ['Regesta'], overwrite: false})
YIELD nodeCount, relationshipCount
```

Importing into the database the file was exported from duplicates all 35 nodes unless the
merge configuration matches them. Import into an empty or a separate database.

## All regests

No procedure exports the whole database as one JGF file: the `atag.export.*` procedures take
a list of nodes and relationships, which for the whole register (212,000 regests, 3.7
million annotations) would be built in memory as a single list. Export one file per regest
instead. The list is then built per row, never across regests.

### Export

```cypher
MATCH (r:Regesta)
CALL (r) {
  CALL apoc.path.subgraphAll(r, {relationshipFilter: 'HAS_ANNOTATION>|<PART_OF'}) YIELD nodes, relationships
  CALL atag.export.jgf.list(nodes, relationships, {fileName: 'regest_' + r.uuid + '.json'}) YIELD value
  RETURN value
}
RETURN count(*) AS files
```

Try it on a few regests first by limiting the outer query: `MATCH (r:Regesta) WITH r LIMIT 10 CALL (r) {...}`.

### Import

Cypher cannot list a directory, so export the file names from the source database:

```cypher
MATCH (r:Regesta) RETURN 'regest_' + r.uuid + '.json' AS file
```

Save them one per line as `files.txt` and import them in a loop, for example with
`cypher-shell`:

```bash
cat files.txt | while read f; do
  echo "CALL atag.import.jgfFile('$f', {propertyKey:'uuid', labels:['Regesta','Content','Annotation'], overwrite:false}) YIELD nodeCount, relationshipCount;"
done | cypher-shell -d neo4j
```

`uuid` is the merge key, so nodes that occur in several files are created once. Create an
index on `uuid` for these labels in the target database beforehand, otherwise the import is
very slow.

### What the files do not contain

* departments and volumes (the `PART_OF` hierarchy above the regest)
* the entities (`Place`, `Person`) and their annotations
* the `next` links between regests

These need separate exports, following the `COLLECT` statement in `RegestaExampleTest` and
[worked example III](worked-example-regesta.html).
