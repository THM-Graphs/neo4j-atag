# 5. The nesting depth of the source decides between annotations over the same range

Date: 2026-09-18

## Status

Accepted

## Context

Annotations are ranges over a text, and the TEI export nests them by containment
(ADR 0002). Two annotations over exactly the same characters contain each other, and the
offsets cannot say which one is the element and which the child. Real editions write such
pairs all the time: `<subst><del>Reipub.</del></subst>`, `<add><choice>…</choice></add>`,
`<p><seg>…</seg></p>`, an empty `<del>` around an empty `<unclear/>`. Serializing them in
the wrong order changes their meaning, and without a rule the order depended on the
iteration order of relationships in the database.

## Decision

* The import records the nesting depth of every inline element in a `depth` property of
  the annotation (stand-off annotations have none).
* The TEI export uses it as a tie-break, and only when every annotation of a text carries
  it: candidates are ordered by start offset, then depth, then end offset, and an
  annotation contains another only if it is nested less deeply. Without depths the rule of
  ADR 0002 applies unchanged - longest range first.
* `depth` is a reserved property: it is never written as an attribute.

## Consequences

* Same-range pairs from an XML source come back in their original nesting, and a document
  that was a tree is exported without a single deferred annotation.
* Same-range annotations of equal depth are siblings when empty and conflict otherwise,
  in which case the later one is deferred to `<standOff>` - writing both inline would
  duplicate text.
* The absolute value is not stable across a roundtrip (an exported witness gains an
  `<ab>` level); only the order among the annotations of one text matters, and that is.
* Annotations created by hand, without a depth, fall back to the previous behaviour; a
  text mixing both kinds is treated as if none had a depth.
