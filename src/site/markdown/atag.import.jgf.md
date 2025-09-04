# atag.import.jgf

This procedure imports a graph from a JSON Graph Format (JGF) string.

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
