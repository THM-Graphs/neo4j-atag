# `atag.text.import.html`

## Description

Generate annotation nodes out of HTML tags. The function takes a node containing html text and a property key for the html property. It returns new annotation nodes.
For HTML parsing the function uses the [jsoup](https://jsoup.org/) library.

| name              | type   | description                                    | default value  |
|-------------------|--------|------------------------------------------------|----------------|
| startNode         | node   | start node containing html text                |                |
| propertyKey       | string | property name for html property                | text           |
| label             | string | label for new annotation nodes                 | Annotation     |
| plainTextProperty | string | property name for plain text                   | plainText      |
| relationshipType  | string | relationship type between for annotation nodes | HAS_ANNOTATION |
|                   |        |                                                |                |
| return value      | node   | new annotation nodes                           |                |

## Example

```cypher
CREATE (r:Text {identifier:'[RI XIII] H. 15 n. 101', summary:'Kg. F. teilt Kammerer und Rat der Stadt Regensburg mit, daß er als Vormund des Königs (von Böhmen und Ungarn) Ladislaus (Postumus) einerseits sowie die Brüder Hans und Heinrich, Hzz. zu Lüben (<em>Löbin</em>)<sup>1</sup>, andererseits glauben, Ansprüche auf die Länder und Städte Liegnitz und Goldberg<sup>2</sup> zu haben. Deshalb habe er auf den <em>nechsten montag nach sant Veits schierstkunftigen</em> (Juni 21) einen Tag nach Breslau angesetzt und an mehrere Fürsten, Herren und Mannen geschrieben, diesen Tag zu besuchen<sup>3</sup>. <em>Als richter</em> in dieser Angelegenheit habe er den Bf. Peter (Nowag) von Breslau <em>geordnet</em><sup>4</sup>; er bittet sie, ebenfalls eine Botschaft zu schicken, um Reinprecht von Ebersdorf und anderen, die er dorthin senden werde, <em>rat und beystand ze tun</em>. Er (Kg. F.) habe auch den Breslauern befohlen, ihnen hierzu Sicherheit und Geleit zu erteilen<sup>5</sup>.'});
MATCH (t:Text {identifier:'[RI XIII] H. 15 n. 101'})
CALL atag.text.import.html(t, 'summary', 'Annotation', 'text', 'HAS_ANNOTATION') YIELD node
RETURN node;
```