# `atag.text.xpath`

## Description

A function that evaluates an XPath 3.1 expression against an XML string. Matched elements
are returned serialized - without XML declaration and without any re-indentation, so a
subtree cut out of a document can be stored and imported on its own with its character
offsets intact. Attributes, text nodes and atomic values are returned as their string
value.

The `*:name` wildcard matches an element in any namespace, so no namespace declaration is
needed for TEI.

## Parameters

| name         | type         | description                                         | default value |
|--------------|--------------|-----------------------------------------------------|---------------|
| xml          | String       | the document                                        |               |
| xpath        | String       | XPath 3.1 expression                                |               |
|              |              |                                                     |               |
| return value | list of strings | the matches, in document order; empty if none  |               |

## Example

```cypher
MATCH (c:Corpus {uuid: 'sozinianer'})
UNWIND atag.text.xpath(c.xml, '/*:teiCorpus/*:TEI') AS letterXml
CREATE (l:Letter {uuid: atag.text.xpath(letterXml, '/*:TEI/@xml:id')[0], xml: letterXml})
CREATE (l)-[:PART_OF]->(c)
```

`[0]` of an empty list is `null`, so an attribute the element does not have sets no
property. The [second worked example](worked-example-letter.html) takes a whole corpus
apart this way.
