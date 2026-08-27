# Applied-Text-As-Graph (ATAG) module for Neo4j

This project provides a collection of procedures and functions for working with text as a graph in Neo4j.

Text and markup are imported into the graph and exported back out through profile-driven
pipelines: an import parses and validates a source document, extracts its structure, maps
it into the project's vocabulary and constructs the graph; an export traverses the graph,
maps it back and serializes it as JGF, stand-off JSON/XML or TEI/XML. What a project's
structures mean is declared in an [import or export profile](profiles.html) rather than
built into the procedures.
