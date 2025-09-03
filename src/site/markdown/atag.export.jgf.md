# `atag.export.jgf`

Exports a graph in [JGF (JSON Graph Format)](https://jsongraphformat.info/) from a list of nodes and relationships.

## Signature

```cypher
atag.export.jgf(nodes, relationships) :: STRING
```

- **nodes**: List of nodes (`List<Node>`)
- **relationships**: List of relationships (`List<Relationship>`)
- **Returns**: JGF string (JSON)

## Example

Export a subgraph with APOC and convert it to JGF format:

```cypher
MATCH (start:Person {name: 'Alice'})
CALL apoc.expand.subgraphAll(start, {maxLevel:2}) YIELD nodes, relationships
RETURN atag.export.jgf(nodes, relationships) AS jgf
```

The result is a JSON string in JGF format describing the subgraph.

## Features

- Nodes and relationships are exported with their properties and labels.
- Supports various property types (String, Integer, Long, Double, Boolean, LocalDate).

## Typical Usage

- Export graph data for visualization or further processing in external tools.
- Integration with APOC procedures for flexible subgraph extraction.

## Example Output

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
