# 3. The project model as a meta graph

Date: 2026-08-27

## Status

Accepted

## Context

Profiles refer to the project model in every call. Repeating the model in each procedure
call is error-prone, and it keeps the model out of reach of Cypher: queries cannot ask
which labels are content, which are annotations, or which relation carries an annotation.

## Decision

`atag.model.meta.write` stores the project model as graph data next to the instance data:
each RAMEN concept becomes a `(:Meta:Concept)` node, each project label a `(:Meta:Type)`
node with a `REFINES` relationship to its concept, and the RAMEN relations become
relationships between the concept nodes, carrying the relationship types the instance data
uses. `atag.model.meta.read` reads it back.

A profile can then say `model: 'meta'` instead of spelling the model out.

## Consequences

* The model is queryable and traversable with the same vocabulary as the data it describes.
* Concepts the meta graph does not describe keep their defaults, so a partially modelled
  project still works.
* The meta graph is part of the same database as the instance data. Exports that traverse
  from a content node do not reach it, but an unfiltered `MATCH (n)` does - a project that
  exports whole databases should exclude the `Meta` label.
