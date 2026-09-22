# 6. A profile is one named artifact, and the import builds the document hierarchy

Date: 2026-09-22

## Status

Accepted. Completes the open point of ADR 0001 ("the profile format is currently an
untyped config map").

## Context

The worked example of a real edition (`src/site/markdown/worked-example-letter.md`) needed
four statements before a single text was imported: hand-written Cypher cutting the
`<teiCorpus>` into `Corpus`, `Letter` and `Witness` nodes with eight XPath literals, and
three near-identical calls for the registers - with the dictionary and the reference
attributes repeated in the import and the export call afterwards.

Measured against *Towards TEI/XML Import and Export via Stand-off Annotation and
Graph-Based Data Models* (JTEI 2026), two things were missing. The paper describes the
project model as "the central control point for import and export" whose specifications
"are expressed in a configuration file" holding the vocabularies, the mappings of both
directions and the validation rules - one artifact, not a map repeated per call. And its
import is "parsed, validated, *structurally analysed*, mapped according to the import
profile … and then written as graph data", while `GraphConstructor` could only ever write
annotations onto a content node that already existed: the hierarchy of a corpus was the one
part of the model the import could not produce.

## Decision

* A profile is **one named artifact**. `atag.profile.write` stores it as a
  `(:Meta:Profile {name, json})` node next to the meta graph and writes its `model` section
  to that graph; any call then says `{profile: 'name'}`. `atag.profile.parse` reads a
  profile that lives in version control, so the same artifact can be a file.
* A profile has **sections**: `model`, `dictionary`, `documents`, `registers`, and
  `import` / `export` for the keys that mean something in one direction only.
  `atag.profile.Profiles.resolve` flattens the named profile, the section of the current
  direction and the keys of the call itself - later wins - into the map the profile classes
  have always read, so every existing flat call keeps working.
* The import **builds the document hierarchy**. The `documents` section lists the levels
  outermost first; each selects its parts within the fragment of the level above, says
  whether they are collections or content, and carries the identifier, properties, header
  and source of those nodes. `atag.text.import.corpus` creates them, links each `PART_OF`
  the level above, imports the `registers` as entities of the outermost document, and runs
  the text of every content level through the existing pipeline.

## Consequences

* A corpus enters the graph in one call, and the same name drives the export. The worked
  example is a profile and two calls instead of six statements.
* A profile is stored as JSON in one property, not decomposed into nodes like the model: it
  nests - sections, lists of maps - and a Neo4j property cannot. The model stays
  decomposed, because it is flat and worth traversing in Cypher.
* Two XPath engines remain: the document structure is selected with Saxon-grade syntax and
  the text phases keep the JDK's XPath 1.0. Rather than ask a profile to spell a selection
  two ways, `atag.text.XPathSyntax` rewrites the `*:name` wildcard into
  `*[local-name()='name']` for the older engine. Switching the text phases to Saxon would
  remove the seam, at the price of unwrapping Saxon nodes back to the DOM the offsets are
  computed from.
* A level's label must be one the model knows for its concept; otherwise the import fails
  rather than producing a graph the export cannot recognize.
* Registers are imported before any text, so references resolve against entities that are
  already there. An entity declared for a corpus is `PART_OF` it exactly once, however
  often the register is imported.
* `entityLabels` replaces the habit of redefining the model per call
  (`model: {entity: ['Entity','Person']}`) when a register carries an extra label.
* What the paper leaves as future work is still future work: JSON Schema validation of
  profiles, and ODD-based validation of the sources.
