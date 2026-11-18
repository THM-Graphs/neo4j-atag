# 4. What the model does not describe travels verbatim; documents nest as `<TEI>`

Date: 2026-09-18

## Status

Accepted. Supersedes the "header is placeholder content" consequence of ADR 0002.

## Context

Validating the pipeline against a document from a real edition (*Die sozinianischen
Briefwechsel*, see `src/site/markdown/worked-example-letter.md`) showed what a semantic
roundtrip loses when the model is the only thing that survives: four `<teiHeader>`s with
correspondents, dates, abstract and manuscript description; the `<persName>`, `<birth>`
and `<idno>` children of 83 entity declarations; 38 references to letters and bibliography
entries that are not declared in the file; and the corpus structure itself, `<teiCorpus>`
holding a letter holding two witnesses, each a `<TEI>` of its own.

The RAMEN model has no concept for any of this, and modelling it would make the model an
image of TEI. Dropping it makes the export incomplete.

## Decision

* An XML fragment the model has no place for is kept **verbatim** as a string property of
  the node it belongs to, and the TEI export writes it back as it was. This covers the
  document header (`headerXPath` / `headerProperty`, default `teiHeader`) and entity
  declarations (`entitySourceProperty`). Fragments are copied event by event into the
  output, so they can only be re-emitted well-formed.
* An anchor that carries a header is written as a **nested `<TEI>`** inside the `<TEI>` of
  its parent, with its identifier and properties as attributes; a root without a header
  whose parts have one is written as a `<TEI>` with a placeholder header, so that no
  header is lost for lack of a place to write it. A content node written this way holds
  its text in `<text><body><ab>`; a collection holds its header-less parts there and its
  header-bearing parts as further nested `<TEI>`s. One `<standOff>` at the root carries
  every deferred annotation and every entity of the export.
* An entity declaration kept verbatim is written back into the TEI list its declaring
  element belongs to (`listPerson`, `listPlace`, `listOrg`, `listEvent`, else `list`); the
  declaring element is kept in the entity's `tag`.
* A **reference the graph cannot resolve** stays a property named after the attribute it
  was written in, holding the pointer as written. On export it is merged back into the
  reference attribute alongside the resolved references. `referenceAttribute` in the
  export profile names that attribute (`ref` by default, `corresp` in the edition).
* Entities may be `PART_OF` a collection, so an export starting at a corpus reaches its
  whole register. `atag.text.import.entities` imports a register on its own;
  `atag.text.xpath` cuts a corpus into the documents the import works on.

## Consequences

* The roundtrip of the edition letter is complete except for the `<head>` and `<desc>` of
  the register lists, which belong to no entity. Verbatim content is opaque to the graph:
  the `<rs>` references inside a header's abstract are not relationships.
* Verbatim fragments are assumed to be in the TEI namespace; unprefixed elements are
  adopted into it, prefixed ones keep their declarations.
* A header on a node whose parent is written as a `<div>` has no legal place and is not
  written. The rule is "nested `<TEI>` under a `<TEI>`", never a `<teiHeader>` in a `<div>`.
* A deferred annotation with a property named `target` cannot carry it: the name is taken
  by the stand-off pointer.
* The export contract `tei-atag-export.xsd` now allows nested `<TEI>`, any header content
  and the TEI lists in `<standOff>`; `div` is declared locally so an inline `<div>`
  annotation is not held to the collection shape.
* `xml:id` is written only where the node has an identifier or a pointer needs one, so a
  document imported with `addUuid: false` comes back without invented identifiers.
* Parts of a collection are unordered in the graph; the export orders them by identifier.
