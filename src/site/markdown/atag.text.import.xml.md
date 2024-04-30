# `atag.text.import.xml`

## Description

Generate annotations from a property containing a (TEI) XML document.

## Parameters

| name              | type   | description                                    | default value          |
|-------------------|--------|------------------------------------------------|------------------------|
| startNode         | node   | start node containing html text                |                        |
| propertyKey       | string | property name for html property                | text                   |
| xpath expression  | string | xpath used as a filter                         | /TEI/text/body//node() |
| label             | string | label for new annotation nodes                 | Annotation             |
| plainTextProperty | string | property name for plain text                   | plainText              |
| relationshipType  | string | relationship type between for annotation nodes | HAS_ANNOTATION         |
|                   |        |                                                |                        |
| return value      | node   | new annotation nodes                           |                        |

## Example

```cypher
CREATE (t:Text{id:1, text: atag.text.load("https://git.thm.de/aksz15/teixml2spo/-/raw/master/patzig.xml")})
WITH t
CALL atag.text.import.xml(t, 'text', '/TEI/text/body//node()', 'Annotation', 'plainText') YIELD node
RETURN node
```