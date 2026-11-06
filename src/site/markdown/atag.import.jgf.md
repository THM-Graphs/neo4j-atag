# atag.import.jgf

This procedure imports a graph from a JSON Graph Format (JGF) string.

Requires `dbms.security.procedures.unrestricted` to cover `atag.import.*`, see
[installation](installation.html).

## Syntax

```cypher
CALL atag.import.jgf(json :: STRING) YIELD nodeCount, relationshipCount
```

### Parameters

- `json`: The JGF-formatted JSON string containing the graph data

### Results

- `nodeCount`: Number of nodes imported
- `relationshipCount`: Number of relationships imported

## Example

```cypher
CALL atag.import.jgf('
{
  "graph": {
    "nodes": {
      "n1": {
        "label": "Person",
        "metadata": {
          "name": "Alice",
          "age": 30
        }
      },
      "n2": {
        "label": "Person",
        "metadata": {
          "name": "Bob"
        }
      }
    },
    "edges": [{
      "source": "n1",
      "target": "n2",
      "relation": "KNOWS"
    }]
  }
}
') YIELD nodeCount, relationshipCount
```

## Property types

Values are restored by their JSON form: strings that parse as an ISO date become dates,
integers, decimals and booleans keep their type, and an object
`{"type": "Point", "srid": ..., "coordinates": [...]}` becomes a `Point`. An array of
integers becomes a `long[]`, of numbers a `double[]`, of booleans a `boolean[]`; any other
array, including an empty one, becomes a `String[]`.
