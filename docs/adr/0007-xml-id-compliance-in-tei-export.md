# 7. Identifiers are rewritten to valid `xml:id` values in the TEI export

Date: 2026-10-06

## Status

Accepted

## Context

Node identifiers come from the graph and are unconstrained: UUIDs starting with a digit,
URNs with colons, values with whitespace. `xml:id` is an NCName, and a pointer such as
`ref="#a b"` is split at the blank. The exporter wrote identifiers verbatim, so the
result could be invalid or point at the wrong thing. Generated identifiers (`atag-N`)
could also collide with identifiers from the graph.

## Decision

* Every identifier written as `xml:id`, and every `#`-pointer or string-range pointer to
  one, passes through `XmlIds`, once per export.
* Characters outside letters, digits, `_`, `-` and `.` become `_`; an identifier that does
  not start with a letter or `_` is prefixed with `_`.
* Rewriting is silent and deterministic: one graph identifier always yields one value, so
  elements and pointers agree. Different identifiers that would collapse into the same
  value are told apart with a `_2`, `_3`... suffix.
* Generated identifiers skip every value already taken.
* The graph is not changed, and identifiers that were already valid are unchanged.

## Consequences

* The output is valid regardless of the graph's identifiers; the original is not
  recoverable from a rewritten value, and a re-import yields the rewritten one.
* Two distinct elements that carry the same graph identifier still receive the same
  `xml:id`; the identifier is the node's identity and is not repaired.
