# `atag.export.jgf.fromNode`

Traverses from a start node and exports the reachable subgraph as [JGF (JSON Graph Format)](https://jsongraphformat.info/).

Unlike `atag.export.jgf` and `atag.export.jgfFile`, this procedure does not require pre-collected node and relationship lists. Instead, it performs a breadth-first traversal from a given start node, following ATAG-specific relationship types.

## Signature

```cypher
CALL atag.export.jgf.fromNode(startNode, config) YIELD value
```

- **startNode**: The node to start the traversal from (`Node`)
- **config**: Configuration map (`Map<String, Object>`)
- **Returns**: JGF map (`value`)

## Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `includeCharacterChain` | `Boolean` | `true` | Whether to include character-level chain nodes and relationships (`NEXT_CHARACTER`, `TOKEN_START`, `TOKEN_END`, `STANDOFF_START`, `STANDOFF_END`, `CHARACTER_HAS_ANNOTATION`) |
| `annotationTypes` | `List<String>` | `null` (all) | If set, only `Annotation` nodes whose `type` property matches one of the given values are included |

## Traversed Relationship Types

**Outgoing** (always included):
- `HAS_ANNOTATION`
- `NEXT_TOKEN`
- `REFERS_TO`

**Outgoing** (when `includeCharacterChain` is `true`):
- `NEXT_CHARACTER`
- `TOKEN_START`
- `TOKEN_END`
- `STANDOFF_START`
- `STANDOFF_END`
- `CHARACTER_HAS_ANNOTATION`

**Incoming** (always included):
- `PART_OF`

## Example

Export an ATAG text graph starting from a specific node, excluding the character chain:

```cypher
MATCH (start:Text {name: 'myDocument'})
CALL atag.export.jgf.fromNode(start, {includeCharacterChain: false}) YIELD value
RETURN value
```

Export only annotations of specific types:

```cypher
MATCH (start:Text {name: 'myDocument'})
CALL atag.export.jgf.fromNode(start, {annotationTypes: ['person', 'place']}) YIELD value
RETURN value
```

## Typical Usage

- Export a complete ATAG text graph without needing APOC for subgraph collection.
- Selectively export subsets of annotations by filtering on annotation type.
- Exclude the character chain for a smaller, token-level-only export.