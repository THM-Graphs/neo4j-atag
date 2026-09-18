# A worked example II: a corpus letter in and out of the graph, losslessly

The [first worked example](worked-example.html) uses a letter of two sentences. This one
takes a document from a real digital edition through the same pipeline and then checks,
element by element, that the export has the same content as the source. Every statement
and every result below is executed by `LetterExampleTest`, so what you read here is what
the procedures produce.

The document is *Ismaël Boulliau (Paris) an Stanisław Lubieniecki (Hamburg), 6. März
1665* from the edition *Die sozinianischen Briefwechsel* (ed. Kęstutis Daugirdas and
Andreas Kuczera, Johannes a Lasco Bibliothek Emden and Akademie der Wissenschaften und der
Literatur Mainz), published under CC BY 4.0 at
[sozinianer.de/id/MAIN_ed_kbj_wfw_xmb](https://sozinianer.de/id/MAIN_ed_kbj_wfw_xmb). The
file is in the repository as `src/test/resources/import-export/LETTER_MAIN_ed_kbj_wfw_xmb.xml`.

Two decisions this example forced are recorded as architecture decision records:
`docs/adr/0004-verbatim-passthrough-and-nested-tei.md` (what the model does not describe
travels verbatim) and `docs/adr/0005-source-nesting-depth-as-serialization-tie-break.md`
(how two annotations over the same characters keep their order).

## The source document

85 KB of TEI. Its outline:

```xml
<teiCorpus xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader>…the edition…</teiHeader>
  <standOff>
    <listPerson><person xml:id="Boulliau">…</person> ×35</listPerson>
    <listPlace><place xml:id="Paris">…</place> ×8</listPlace>
    <list n="terms"><item xml:id="ed_vnp_dyc_ydb">…</item> ×40</list>
  </standOff>
  <TEI xml:id="MAIN_ed_kbj_wfw_xmb" type="letter" n="cover_letter">
    <teiHeader>…correspondents, date, abstract…</teiHeader>
    <TEI xml:id="ed_kbj_wfw_xmb" n="reference_witness" type="letter">
      <teiHeader>…the manuscript…</teiHeader>
      <text><body><div type="letter">…509 elements over 16 677 characters…</div></body></text>
    </TEI>
    <TEI xml:id="ed_abg_zbc_nlb" corresp="ed_kbj_wfw_xmb" type="letter">
      <teiHeader>…the print…</teiHeader>
      <text><body><div type="letter">…23 elements over 3 781 characters…</div></body></text>
    </TEI>
  </TEI>
</teiCorpus>
```

A register of 83 entities, declared once for the corpus with names, dates and authority
identifiers as child elements; a cover letter with two witnesses, each a `<TEI>` with a
header of its own; and a text with everything an edition writes inline - `<rs>` references
to the register (by bare identifier in `@corresp`), deletions, additions, substitutions,
abbreviations with their expansions, editorial notes with references of their own, page
and line breaks. Things that make this document a test of the model rather than of the
parser:

* Four headers, each carrying metadata the RAMEN model has no concept for.
* The declarations of the register have content, not just attributes.
* 38 references point at things that are not declared in this file: other letters of the
  edition and entries of a bibliography.
* Eight pairs of elements cover exactly the same characters - `<subst><del>…</del></subst>`,
  `<add><choice>…</choice></add>`, `<p><seg>…</seg></p>` - which character offsets alone
  cannot tell apart.
* Two entities of the register are referenced only from the abstract in the letter's header.

## 1. Declare the project model

```cypher
CALL atag.model.meta.write({
  model: {
    collection: ['Corpus', 'Letter'],
    content:    ['Witness'],
    annotation: ['Annotation'],
    entity:     ['Entity']
  }
}) YIELD value
RETURN value
```

The corpus and the letter are collections, a witness is a content node. There is no
dictionary in this example: the project's vocabulary *is* TEI, so element names stay in
`tag`, `@type` stays in `type`, and every other attribute becomes a property of the same
name.

## 2. Take the corpus apart

An import call works on one content node holding one document. The corpus therefore has to
be cut into its parts first, which is what [atag.text.xpath](atag.text.xpath.html) is for:
it evaluates an XPath against an XML string and returns the matched elements serialized,
or attribute values as strings. The `*:` prefix matches an element in any namespace.

```cypher
CREATE (c:Corpus {uuid: 'sozinianer', xml: $xml,
                  teiHeader: atag.text.xpath($xml, '/*:teiCorpus/*:teiHeader')[0]})
WITH c
UNWIND atag.text.xpath(c.xml, '/*:teiCorpus/*:TEI') AS letterXml
CREATE (l:Letter {uuid: atag.text.xpath(letterXml, '/*:TEI/@xml:id')[0],
                  type: atag.text.xpath(letterXml, '/*:TEI/@type')[0],
                  n:    atag.text.xpath(letterXml, '/*:TEI/@n')[0],
                  teiHeader: atag.text.xpath(letterXml, '/*:TEI/*:teiHeader')[0]})
CREATE (l)-[:PART_OF]->(c)
WITH l, letterXml
UNWIND atag.text.xpath(letterXml, '/*:TEI/*:TEI') AS witnessXml
CREATE (w:Witness {uuid: atag.text.xpath(witnessXml, '/*:TEI/@xml:id')[0],
                   type: atag.text.xpath(witnessXml, '/*:TEI/@type')[0],
                   n:    atag.text.xpath(witnessXml, '/*:TEI/@n')[0],
                   corresp: atag.text.xpath(witnessXml, '/*:TEI/@corresp')[0],
                   xml: witnessXml})
CREATE (w)-[:PART_OF]->(l)
```

`[0]` of an empty list is `null`, so an attribute the element does not have sets no
property. The headers of the corpus and the letter are kept as they are, in a `teiHeader`
property - there is nothing in the model they could be mapped to, and nothing is lost by
keeping them verbatim (ADR 0004). The attributes of the `<TEI>` elements become properties.

```cypher
MATCH (c:Corpus)<-[:PART_OF]-(l:Letter)<-[:PART_OF]-(w:Witness)
RETURN l.uuid, l.type, l.n, w.uuid, w.n, w.corresp
```

| uuid                | type   | n                 | corresp        |
|---------------------|--------|-------------------|----------------|
| MAIN_ed_kbj_wfw_xmb | letter | cover_letter      | `null`         |
| ed_kbj_wfw_xmb      | letter | reference_witness | `null`         |
| ed_abg_zbc_nlb      | letter | `null`            | ed_kbj_wfw_xmb |

## 3. Import the register

The register is declared once for the corpus, not inside a text, so it is imported with
[atag.text.import.entities](atag.text.import.entities.html), which runs only the entity
phases of the pipeline. One call per list, because the list decides the label:

```cypher
MATCH (c:Corpus {uuid: 'sozinianer'})
CALL atag.text.import.entities(c, 'xml', {
  model: {entity: ['Entity', 'Person']},
  rootElement: 'teiCorpus',
  entityXPath: "/*[local-name()='teiCorpus']/*[local-name()='standOff']/*[local-name()='listPerson']/*[local-name()='person']",
  entityLabelXPath: "normalize-space((.//*[@type='reg'])[1])",
  entitySourceProperty: 'tei'
}) YIELD node
CREATE (node)-[:PART_OF]->(c)
RETURN count(node) AS persons
```

```
persons
35
```

The same call with `Place` and `…/listPlace/place` yields 8, with `Term` and
`…/list[@n='terms']/item` 40. Three profile keys do the work:

* `entityXPath` selects the declarations. The TEI default looks for the
  `<list type="entity">` of the stand-off vocabulary; this edition uses TEI's own lists.
* `entityLabelXPath` is evaluated relative to each declaration and produces the display
  name - here the first child marked as the regularized form, which works for persons,
  places and terms alike.
* `entitySourceProperty` keeps the declaration itself, verbatim, in the named property.
  The model has no place for `<birth>`, `<idno type="uri">` or an alternative name with a
  `<note>` of its own; the property has.

Every entity is linked `PART_OF` the corpus, so that an export starting at the corpus
reaches the whole register - including the entities no text refers to (step 4).

```cypher
MATCH (e:Entity) WHERE e.uuid IN ['Boulliau', 'Paris', 'ed_vnp_dyc_ydb']
RETURN e.uuid, e.tag, e.label, labels(e)
```

| e.uuid         | e.tag  | e.label         | labels(e)      |
|----------------|--------|-----------------|----------------|
| Boulliau       | person | Boulliau Ismaël | Entity, Person |
| Paris          | place  | Paris           | Entity, Place  |
| ed_vnp_dyc_ydb | item   | Komet           | Entity, Term   |

`tag` keeps the element a declaration was written with, like it does for annotations. The
verbatim declaration is what the source wrote:

```xml
<person xml:id="Boulliau" xmlns="http://www.tei-c.org/ns/1.0">
    <persName type="reg">
        <surname>Boulliau</surname>
        <forename>Ismaël</forename>
    </persName>
    <persName type="alt">
        <name>Boullialdus, Ismael</name>
    </persName>
    <birth>1605</birth>
    <death>1694</death>
    <idno type="uri">https://www.deutsche-biographie.de/pnd119277379.html</idno>
    <idno type="uri">https://d-nb.info/gnd/119277379</idno>
</person>
```

## 4. Import the witnesses

```cypher
MATCH (w:Witness)
CALL atag.text.import.tei(w, 'xml', {
  model: 'meta',
  xpath: "/*[local-name()='TEI']/*[local-name()='text']/*[local-name()='body']//node()[not(self::*[local-name()='ab'])]",
  referenceAttributes: ['corresp', 'sameAs'],
  addUuid: false
}) YIELD node
RETURN w.uuid AS witness, count(node) AS annotations
```

| witness        | annotations |
|----------------|-------------|
| ed_kbj_wfw_xmb | 509         |
| ed_abg_zbc_nlb | 23          |

* `xpath` differs from the TEI default in one respect: it keeps `<div>`. The default leaves
  `<div>` and `<ab>` out because the export writes those containers itself; here the
  `<div type="letter">` is part of what the edition wrote, so it becomes an annotation
  like any other element.
* `referenceAttributes` names the attributes that point at entities. The edition uses
  `@corresp` on `<rs>` and `@sameAs` on `<bibl>`.
* `addUuid: false` leaves annotations without an identifier of their own unidentified.
  The source gives an `xml:id` to its 18 notes and to nothing else, and the export writes
  an identifier only where the source had one or where a pointer needs one - so the
  exported text is not littered with generated identifiers.
* The header of each witness is kept on its node by the `headerXPath` default, like the
  headers of the corpus and the letter were kept in step 2.

## 5. What the import produced

The plain text of each witness is exactly the text content of its `<body>`: 16 677
characters for the manuscript, 3 781 for the print, editorial notes included - they are
part of what the edition wrote inline. Over that text sit the annotations, one per element:

```
rs 168, del 80, add 67, subst 27, abbr 24, choice 24, expan 24, p 21, note 18, orig 18, seg 18,
ref 9, bibl 8, hi 6, date 4, pb 4, dateline 3, div 2, opener 2, salute 2, closer 1, lb 1, unclear 1
```

Every annotation has a `depth`, the nesting depth of its element in the source. Most of the
time it is redundant with the offsets; for a deletion without text inside a substitution
over the same range, it is the only thing that says which element contained which:

```cypher
MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(a:Annotation)
WHERE a.startIndex >= 227 AND a.endIndex <= 234
RETURN a.tag, a.startIndex, a.endIndex, a.depth, a.place,
       substring(w.plainText, a.startIndex, a.endIndex - a.startIndex) AS text
ORDER BY a.startIndex, a.depth
```

| a.tag | a.startIndex | a.endIndex | a.depth | a.place     | text      |
|-------|--------------|------------|---------|-------------|-----------|
| subst | 227          | 234        | 5       | `null`      | ` quibus` |
| del   | 227          | 227        | 6       | `null`      | ``        |
| add   | 228          | 234        | 6       | superlinear | `quibus`  |

This is `Ex <subst><del rendition="#s"/> <add place="superlinear">quibus</add></subst>` in
the source. An editorial note is an annotation inside the text it comments on, with
references of its own:

```cypher
MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(n:Annotation {uuid: 'nd14_nmz_m4b'})
MATCH (w)-[:HAS_ANNOTATION]->(a:Annotation)
WHERE a.startIndex <= n.startIndex AND n.endIndex <= a.endIndex AND a.depth >= n.depth - 1
   OR n.startIndex <= a.startIndex AND a.endIndex <= n.endIndex AND a.depth = n.depth + 1
RETURN a.tag, a.type, a.uuid, a.startIndex, a.endIndex, a.depth
ORDER BY a.startIndex, a.depth
```

| a.tag | a.type  | a.uuid       | a.startIndex | a.endIndex | a.depth |
|-------|---------|--------------|--------------|------------|---------|
| seg   | comment | `null`       | 306          | 690        | 5       |
| note  | `null`  | nd14_nmz_m4b | 450          | 690        | 6       |
| rs    | person  | `null`       | 500          | 518        | 7       |
| bibl  | ref     | `null`       | 611          | 635        | 7       |
| bibl  | ref     | `null`       | 649          | 689        | 7       |

`@corresp` became a relationship where the graph has the entity:

```cypher
MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(a:Annotation {tag: 'rs'})-[:REFERS_TO]->(e:Entity)
RETURN a.type, substring(w.plainText, a.startIndex, a.endIndex - a.startIndex) AS text,
       e.uuid, e.label, labels(e)
ORDER BY a.startIndex LIMIT 4
```

| a.type | text                       | e.uuid         | e.label                                                             | labels(e)      |
|--------|----------------------------|----------------|---------------------------------------------------------------------|----------------|
| place  | de rerum Polonicarum statu | Polen          | Polen                                                               | Entity, Place  |
| person | Johann II. Kasimir         | ed_pq1_cqm_ndb | Johann II. Kasimir Wasa, Kg. von Polen und Schweden, Gfs. von Litauen | Entity, Person |
| place  | regno                      | Polen          | Polen                                                               | Entity, Place  |
| term   | Senatoresque               | Senator        | Senator                                                             | Entity, Term   |

Where the graph has no entity to point at - the other letters of the edition, the entries
of a Zotero bibliography - the pointer stays in the attribute it was written in, as a
property. Nothing is invented and nothing is dropped; a later import into a graph that
holds those letters resolves it (ADR 0004):

```cypher
MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(a:Annotation)
WHERE a.corresp IS NOT NULL OR a.sameAs IS NOT NULL
RETURN a.tag, a.type, a.corresp, a.sameAs,
       substring(w.plainText, a.startIndex, a.endIndex - a.startIndex) AS text
ORDER BY a.startIndex LIMIT 3
```

| a.tag | a.type | a.corresp      | a.sameAs                 | text                     |
|-------|--------|----------------|--------------------------|--------------------------|
| rs    | letter | ed_bjj_5dw_xmb | `null`                   | 14                       |
| rs    | letter | ed_wwh_p2w_xmb | `null`                   | 21 decursi Februarii     |
| bibl  | ref    | `null`         | zotero-2065617-MIBGRW3W  | Wyczański, Adelsrepublik |

In numbers, over both witnesses:

| annotations | with an identifier | linked to an entity | pointer kept as property |
|-------------|--------------------|---------------------|--------------------------|
| 532         | 18                 | 138                 | 38                       |

Two entities of the register are referenced by no annotation at all - the abstract in the
letter's header mentions them, and the header is not text the pipeline reads. They are in
the graph because step 3 imported the whole register, and they reach the export because
they are `PART_OF` the corpus:

```cypher
MATCH (e:Entity) WHERE NOT (e)<-[:REFERS_TO]-()
RETURN e.uuid, e.label, labels(e)
```

| e.uuid         | e.label                     | labels(e)     |
|----------------|-----------------------------|---------------|
| Frankreich     | Frankreich                  | Entity, Place |
| ed_cph_zjt_32b | Sejm (polnischer Reichstag) | Entity, Term  |

## 6. Export

```cypher
MATCH (c:Corpus {uuid: 'sozinianer'})
CALL atag.export.tei.fromNode(c, {
  model: 'meta',
  referenceAttribute: 'corresp',
  entitySourceProperty: 'tei',
  ignoreProperties: ['xml']
}) YIELD value
RETURN value
```

`referenceAttribute` writes `REFERS_TO` as `@corresp` instead of the default `@ref`;
`entitySourceProperty` writes the register from the verbatim declarations instead of
building `<item>` elements from properties. The result, outlined:

```xml
<TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="sozinianer">
  <teiHeader>…the corpus header, verbatim…</teiHeader>
  <standOff>
    <listPerson><person xml:id="Anne_von_Oesterreich">…</person> ×35</listPerson>
    <listPlace>…×8</listPlace>
    <list>…×40</list>
  </standOff>
  <TEI xml:id="MAIN_ed_kbj_wfw_xmb" type="letter" n="cover_letter">
    <teiHeader>…the letter header, verbatim…</teiHeader>
    <TEI xml:id="ed_abg_zbc_nlb" corresp="ed_kbj_wfw_xmb" type="letter">
      <teiHeader>…</teiHeader>
      <text><body><ab><div type="letter"><pb facs="…" n="472"/> <opener><dateline><rs corresp="#Paris" type="place">Lutetia Parisiorum</rs> …</ab></body></text>
    </TEI>
    <TEI xml:id="ed_kbj_wfw_xmb" type="letter" n="reference_witness">
      <teiHeader>…</teiHeader>
      <text><body><ab><div type="letter"><pb facs="…" n="285r"/> <opener>…</opener> <p>Ad binas literas tuas diebus <rs corresp="ed_bjj_5dw_xmb" type="letter">14</rs> … Ex <subst><del rendition="#s"/> <add place="superlinear">quibus</add></subst> …</ab></body></text>
    </TEI>
  </TEI>
</TEI>
```

A node that carries a header is written as a `<TEI>` of its own, nested into the `<TEI>` of
its parent, with its identifier and properties as attributes and its header verbatim - so
the corpus comes back as a corpus. Everything in the two texts is written inline: no
annotation had to move to `<standOff>`, because the source was a tree and `depth` tells
the exporter how to nest the eight same-range pairs. A resolved reference is written as
`corresp="#Paris"`, an unresolved one exactly as it was kept, `corresp="ed_bjj_5dw_xmb"`.

## 7. What proves the roundtrip

`LetterExampleTest` compares the export with the source:

* The plain text of each witness equals the text content of the source `<body>`.
* The elements below the exported `<ab>` and below the source `<body>` are walked in
  document order and compared pairwise: same element name, same text content, same
  attributes - 509 and 23 of them. Attribute order and namespace declarations are
  ignored, a pointer is the same with or without its leading `#`, and an identifier the
  export had to invent is not held against it (it invented none).
* The four headers are compared with XMLUnit, ignoring whitespace.
* The 83 entity declarations are compared the same way, matched by `xml:id`.
* The `xml:id`, `type`, `n` and `corresp` attributes of the letter and the witnesses are
  compared.
* The export validates against `src/test/resources/tei-atag-export.xsd`, the contract of
  what the exporter may produce.
* The exported witnesses are imported again with the profile of step 4, and the annotations
  - element name, range, type - and the entity references are the same multiset as before.

## 8. What changed, and what did not survive

The roundtrip is semantic, so the export is not the source byte for byte:

* The root is `<TEI>`, not `<teiCorpus>`; nested documents are `<TEI>` in both.
* The witness text sits in an `<ab>` around the `<div type="letter">`, because the
  exporter always writes the content node's container itself.
* The two witnesses come out in a different order. The graph does not order the parts of
  a collection - `PART_OF` is a set - so the export orders them by identifier to be
  reproducible.
* `corresp` values that resolved to an entity gained a `#`; the ones kept as properties
  did not.
* Attribute order follows the graph, not the source; the unused `xmlns:ns` and
  `xmlns:da` declarations of the manuscript witness are gone.

One thing is genuinely lost: the `<head>` and `<desc>` of the three register lists
("Personen", "alle in den hier gesammelten Texten vorkommenden Personen …"). They belong to
the list, not to any entity, and the model has no node for a list. The only other loss
would be an attribute with a namespace prefix, such as `xml:lang`, on a body element -
property keys cannot carry a colon - which this document does not have.
