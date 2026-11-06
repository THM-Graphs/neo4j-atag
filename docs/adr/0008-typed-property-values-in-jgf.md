# 8. Points and numeric arrays are typed values in JGF

Date: 2026-11-06

## Status

Accepted

## Context

The JGF export supported strings, numbers, booleans, dates and string arrays and failed with
`Unsupported property type` for anything else. A real graph (the Regesta Imperii
register) carries a `Point` property on its regests, so its export was impossible. The import
turned every array into a `String[]`, so a numeric array could not be restored with its type
even if it could have been written.

## Decision

* A `Point` is written as `{"type": "Point", "srid": <crs>, "coordinates": [x, y(, z)]}`, a
  GeoJSON-like object that keeps the coordinate reference system.
* `long[]`, `int[]`, `double[]` and `boolean[]` are written as JSON arrays.
* The import reads the object form back into a `Point`, and gives an array the narrowest
  matching type: integers `long[]`, other numbers `double[]`, booleans `boolean[]`, everything
  else, including an empty array, `String[]`.
* Types that are still unsupported keep failing loudly instead of being dropped.
* The attribute-based formats (TEI, stand-off XML) write points and arrays as comma-separated
  text.

## Consequences

* A graph with points and numeric arrays survives an export and import with the same types.
* `int[]` comes back as `long[]`, as Neo4j stores both identically.
* An empty array carries no element type in JSON and is restored as `String[]`.
* A map-valued property that happens to look like a point object is imported as a `Point`.
* A string that parses as an ISO date (`yyyy-MM-dd`) is imported as a date: JSON cannot say
  that it was a string. This predates this decision and is unchanged; the third worked example
  shows its effect.
