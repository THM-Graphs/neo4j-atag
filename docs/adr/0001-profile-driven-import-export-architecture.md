# 1. Profile-driven import and export architecture

Date: 2026-08-27

## Status

Accepted

## Context

Importing TEI/XML into a graph and exporting graph data back into TEI/XML is not a
syntactic conversion. The same editorial relation can be encoded inline, as an empty
element pointing at anchors, or as stand-off markup, and which TEI structures count as
which annotation type is a project-specific decision (see *Towards TEI/XML Import and
Export via Stand-off Annotation and Graph-Based Data Models*, JTEI 2026).

Until now both directions were single methods that hard-coded these decisions: element
names became `tag` properties, `Text` and `Annotation` were fixed labels, and the export
scope lived in a handful of config keys read at the point of use. A project with a
different vocabulary could not be served without changing code.

## Decision

Import and export are structured as pipelines whose phases are controlled by a profile:

* **Import** (`atag.text.pipeline`) runs four phases - parse and validate (`SourceReader`),
  extract structure (`StructureExtractor`), dictionary mapping (`StructureMapper`), graph
  construction (`GraphConstructor`). Only the first two phases know the markup language,
  so XML and HTML share phases 3 and 4.
* **Export** (`atag.export`) runs two phases - graph traversal (`SubgraphCollector`) and
  dictionary mapping (`DocumentMapper`) - before a format serializes the result.
* A **profile** (`atag.profile.ImportProfile`, `atag.profile.ExportProfile`) carries the
  project model, the dictionary and the scope of a transformation. It is passed as the
  procedure's config map, so the same procedure serves different projects.
* The generic vocabulary is the RAMEN meta-model (`atag.model.Ramen`): the concepts
  *Collection*, *Content*, *Entity*, *Annotation* and the relations `PART_OF`,
  `HAS_ANNOTATION`, `REFERS_TO`. A `ProjectModel` refines them with the labels and
  relationship type names a concrete project uses.

## Consequences

* Adding a format means implementing `Exporter` (or `DocumentExporter`); adding a source
  language means implementing `SourceReader` and `StructureExtractor`. Neither touches the
  other phases.
* Behaviour that used to be implicit is now declared. The existing procedures keep their
  signatures by building a profile from their positional arguments, so nothing changes for
  current users.
* A project that uses different labels no longer needs a fork; it needs a profile.
* The profile format is currently an untyped config map. Formalizing it (JSON Schema) and
  validating it is left for later, as is ODD-based validation of the source document.
