# 2. TEI serialization: inline where possible, stand-off where necessary

Date: 2026-08-27

## Status

Accepted. ADR 0004 replaces the placeholder header with the verbatim header of the source
and adds nested `<TEI>` documents; ADR 0005 refines the nesting rule for annotations over
the same range.

## Context

The graph stores annotations as independent objects over character ranges, so overlap is
unproblematic. XML cannot express arbitrary overlap in one hierarchy, and TEI offers
several mechanisms for the cases that do not fit - milestones, fragmentation, pointers,
stand-off markup. An export therefore has to decide, per annotation, how the relation is
serialized.

## Decision

`atag.export.tei.*` writes an annotation inline when its range nests properly into the
annotations already placed, and into `<standOff><listAnnotation>` otherwise. Deferred
annotations point back at the text with the TEI XPointer scheme
`string-range(id, start, length)`; an annotation on an annotation points at that
annotation's `xml:id`. `serialization: 'standoff'` in the profile defers every annotation.

Collections become `<div>`, content nodes become `<ab>` carrying the text, entities are
declared in `<standOff>` as `<list type="entity"><item xml:id=… n=… type=…/></list>`, and
`REFERS_TO` becomes `@ref`. The vocabulary shared by both directions lives in
`atag.profile.StandoffVocabulary`.

Character offsets are exported as they are stored, and `endIndex` is treated as exclusive.

## Consequences

* The export is semantically, not lexically, reversible: `atag.text.import.tei` resolves
  both encodings back to the same annotation nodes, but the serialization a roundtrip
  produces may differ in shape from the document that was imported.
* No whitespace is inserted inside `<text>`, because it would shift the character offsets
  every annotation refers to. TEI output is therefore not indented.
* Property keys that are not valid XML names cannot become attributes and are dropped from
  the TEI serialization; they remain available in the JGF and stand-off JSON formats.
* The subset the exporter may produce is pinned by `src/test/resources/tei-atag-export.xsd`,
  and every exported document in the tests is validated against it - so a change to the
  serialization has to be a deliberate change to that contract.
* A TEI header is written with placeholder content. Real metadata (`<teiHeader>` from
  collection properties) is future work, as is ODD-based validation of the output.
