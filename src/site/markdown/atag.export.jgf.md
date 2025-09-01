# `atag.export.Jgf`

Exportiert einen Graphen im [JGF (JSON Graph Format)](https://jsongraphformat.info/) aus einer Liste von Knoten und Beziehungen.

## Signatur

```cypher
atag.export.jgf(nodes, relationships) :: STRING
```

- **nodes**: Liste von Knoten (`List<Node>`)
- **relationships**: Liste von Beziehungen (`List<Relationship>`)
- **Rückgabe**: JGF-String (JSON)

## Beispiel

Exportiere einen Subgraphen mit APOC und konvertiere ihn ins JGF-Format:

```cypher
MATCH (start:Person {name: 'Alice'})
CALL apoc.expand.subgraphAll(start, {maxLevel:2}) YIELD nodes, relationships
RETURN atag.export.jgf(nodes, relationships) AS jgf
```

Das Ergebnis ist ein JSON-String im JGF-Format, der den Subgraphen beschreibt.

## Eigenschaften

- Knoten und Beziehungen werden mit ihren Eigenschaften und Labels exportiert.
- Unterstützt verschiedene Property-Typen (String, Integer, Long, Double, Boolean, LocalDate).

## Typische Anwendung

- Export von Graphdaten zur Visualisierung oder Weiterverarbeitung in externen Tools.
- Integration mit APOC-Prozeduren zur flexiblen Subgraph-Extraktion.

## Beispielausgabe

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
