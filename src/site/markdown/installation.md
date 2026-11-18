# Installation

neo4j-atag requires Neo4j >= 5.0.
Download the latest [release](https://github.com/THM-Graphs/neo4j-atag/releases) and copy the jar file to the `plugins` directory of your Neo4j installation.

If you use the Neo4j Desktop, you can install the plugin via the "3 dots" menu.
Use `Open folder` and `plugins` to copy the jar file to the correct directory.

For a docker based Neo4j deployment you can mount the jar file to the plugins directory.

A restart of Neo4j is required to load the plugin.

## Configuration

The export procedures and the JGF importer can write to and read from Neo4j's import
directory, which requires access to the database configuration. Neo4j grants that only to
procedures that are explicitly trusted, so add to `neo4j.conf`:

```
dbms.security.procedures.unrestricted=atag.export.*,atag.import.*
```

Sandboxing is decided per procedure class rather than per call, so without this setting
*every* call into those two groups fails - including the ones that only return their
result - with

```
atag.export.tei.fromNode is unavailable because it is sandboxed
and has dependencies outside of the sandbox.
```

The remaining procedures (`atag.chains.*`, `atag.text.*`, `atag.model.*`) run inside the
sandbox and need no configuration.

For a docker deployment the same setting is passed as an environment variable:

```
NEO4J_dbms_security_procedures_unrestricted=atag.export.*,atag.import.*
```

The `docker/docker-compose.yml` of this repository sets it alongside the equivalent APOC
configuration.

