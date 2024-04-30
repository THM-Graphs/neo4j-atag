# `atag.text.xslt`

## Description

Transforms a text using an [XSLT](https://www.w3.org/TR/xslt20/) stylesheet. 
The result of the transformation is returned as a string.
Since the Saxon HT engine is used the stylesheet can use XPath 2.0.

## Parameters

| name         | type   | description            | default value |
|--------------|--------|------------------------|---------------|
| text         | String | text to be transformed |               |
| xslt         | String | XSLT stylesheet        |               |
|              |        |                        |               |
| return value | String | transformed text       |               |

## Example

```cypher
RETURN atag.text.xslt(
 atag.text.load('https://gitlab.rlp.net/adwmainz/digicademy/sbw/tei2json/-/raw/master/xml/Briefe/Lubieniecki_Gabriel_ua/1636-08-04_Ch._Lubieniecki_Sieniuta_j1y_qlm_zdb.xml?ref_type=heads',
 atag.text.load('https://gitlab.rlp.net/adwmainz/digicademy/sbw/tei2json/-/raw/master/xsl/standoff_property.xsl'))
```