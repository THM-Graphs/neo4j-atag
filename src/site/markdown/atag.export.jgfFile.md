# `atag.export.jgfFile`

Exports a graph in [JGF (JSON Graph Format)](https://jsongraphformat.info/) and writes it to a file. 
The file location is the import folder of the Neo4j database.

## Signature

```cypher
CALL atag.export.jgfFile(nodes, relationships, filename) YIELD value
```

- **nodes**: List of nodes (`List<Node>`)
- **relationships**: List of relationships (`List<Relationship>`)
- **filename**: Name of the output file (in the import folder) - must not contain path separators (`String`)
- **Returns**: Filename (`value`)

## Example

Export a subgraph with APOC and write it as a JGF file:

```cypher
MATCH (start:Person {name: 'Alice'})
CALL apoc.expand.subgraphAll(start, {maxLevel:2}) YIELD nodes, relationships
CALL atag.export.jgfFile(nodes, relationships, 'myExport.json') YIELD value
RETURN value
```

The file `myExport.json` will be placed in the import folder and contains the graph in JGF format.

## Features

- Exports nodes and relationships with all properties and labels.
- Supports various property types (String, Integer, Long, Double, Boolean, LocalDate).
- Filename must not contain path separators.

## Typical Usage

- Export graph data for further processing or visualization in external tools.
- Integration with APOC procedures for flexible subgraph extraction and file export.

## Example Output (File Content)

```json
{
  "graph": {
    "label": "2024-06-07T12:34:56.789+00:00",
    "directed": true,
    "nodes": {
      "0": {
        "label": "Person",
        "metadata": {
          "name": "Alice",
          "dob": "2012-06-01",
          "height": 175
        }
      },
      "1": {
        "label": "Person",
        "metadata": {
          "name": "Bob"
        }
      }
    },
    "edges": [
      {
        "source": "0",
        "target": "1",
        "relation": "KNOWS"
      }
    ]
  }
}
```
