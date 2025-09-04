# atag.import.jgfFile

This procedure imports a graph from a JSON Graph Format (JGF) file.

## Syntax

```cypher
CALL atag.import.jgfFile(filename :: STRING) YIELD nodeCount, relationshipCount
```

### Parameters

- `filename`: Name of the JGF file to import. The file must be located in Neo4j's import directory.

### Results

- `nodeCount`: Number of nodes imported
- `relationshipCount`: Number of relationships imported

## Example

Assuming you have a file named `graph.json` in Neo4j's import directory:

```cypher
CALL atag.import.jgfFile('graph.json')
YIELD nodeCount, relationshipCount
```

## File Format

The file should contain a valid JGF-formatted JSON document. Example format:

```json
{
  "graph": {
    "nodes": {
      "n1": {
        "label": "Person",
        "metadata": {
          "name": "Alice",
          "dob": "1990-01-01"
        }
      }
    },
    "edges": [{
      "source": "n1",
      "target": "n2",
      "relation": "KNOWS",
      "metadata": {
        "since": "2020-01-01"
      }
    }]
  }
}
```

