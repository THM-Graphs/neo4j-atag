<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:tei="http://www.tei-c.org/ns/1.0"
                exclude-result-prefixes="#all" version="3.0">

    <xsl:output method="text" encoding="UTF-8" indent="no" xml:space="preserve" omit-xml-declaration="yes"/>

    <xsl:template match="/|*|text()|@*">
        <xsl:copy>
            <xsl:apply-templates select="* | text() | @*"/>
        </xsl:copy>
    </xsl:template>

    <!--    <xsl:strip-space elements="tei:body"/>-->

    <!--    <xsl:strip-space elements="tei:div"/>-->

    <xsl:strip-space elements="tei:choice"/>

    <xsl:strip-space elements="tei:subst"/>

    <xsl:strip-space elements="tei:seg"/>

    <!--    <xsl:strip-space elements="tei:opener tei:closer tei:address"/>-->
    <!--    <xsl:preserve-space elements="tei:dateline tei:salute tei:signed tei:addrLine"/>-->

    <!-- whitespace normalization for mixed content -->
    <xsl:template match="text()">
        <!-- Retain one leading space if node isn't first, has non-space content, and has leading space-->
        <xsl:if test="position() != 1 and matches(., '^\s') and normalize-space() != ''">
            <xsl:text> </xsl:text>
        </xsl:if>
        <xsl:value-of select="normalize-space(.)"/>
        <!-- Retain one trailing space if … -->
        <xsl:choose>
            <!-- … node isn't last, isn't first, and has trailing space -->
            <xsl:when test="position() != last() and position() != 1 and matches(., '\s$')">
                <xsl:text> </xsl:text>
            </xsl:when>
            <!-- … node isn't last, is first, has trailing space, and has non-space content   -->
            <xsl:when test="position() != last() and position() = 1 and matches(., '\s$') and normalize-space() != ''">
                <xsl:text> </xsl:text>
            </xsl:when>
            <!-- … node is an only child, and has content but it's all space -->
            <xsl:when test="last() = 1 and string-length() != 0 and normalize-space() = ''">
                <xsl:text> </xsl:text>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <!-- whitespace normalization for block-level elements -->
    <!-- this template was written incorrectly and matches non-empty text nodes and suppresses them -->
    <!--<xsl:template match="(tei:body|tei:div|tei:opener|tei:closer|tei:postscript|tei:address|tei:table|tei:lg)/text()">
        <xsl:choose>
            <xsl:when test="matches(., '^\s+$') and count(preceding-sibling::element()[text()]) eq 0"><xsl:text/></xsl:when>
            <xsl:when test="matches(., '^\s+$') and count(following-sibling::element()[text()]) eq 0"><xsl:text/></xsl:when>
            <xsl:otherwise><xsl:text> </xsl:text></xsl:otherwise>
        </xsl:choose>
    </xsl:template>-->

    <!-- whitespace normalization for block-level mixed content elements -->
    <!-- to do -->

    <xsl:template match="tei:choice[tei:abbr and tei:expan]">
        <abbr xmlns="http://www.tei-c.org/ns/1.0" expansion="{normalize-space(string-join(./tei:expan//text(), ''))}">
            <xsl:apply-templates select="./tei:abbr/child::node()"/>
        </abbr>
    </xsl:template>

    <xsl:template match="tei:choice[tei:orig and tei:reg]">
        <reg xmlns="http://www.tei-c.org/ns/1.0" regularisation="{normalize-space(string-join(./tei:reg//text(), ''))}">
            <xsl:apply-templates select="./tei:orig/child::node()"/>
        </reg>
    </xsl:template>

    <xsl:template match="tei:choice[tei:sic and tei:corr[not(@type = 'deleted')]]">
        <sic xmlns="http://www.tei-c.org/ns/1.0" correction="{normalize-space(string-join(./tei:corr//text(), ''))}" certainty="{./tei:corr/@cert}">
            <xsl:apply-templates select="./tei:sic/child::node()"/>
        </sic>
    </xsl:template>

    <xsl:template match="tei:choice[tei:sic and tei:corr[@type = 'deleted']]">
        <sic xmlns="http://www.tei-c.org/ns/1.0" correction="" isDeleted="true">
            <xsl:apply-templates select="./tei:sic/child::node()"/>
        </sic>
    </xsl:template>

    <xsl:template match="tei:subst">
        <subst xmlns="http://www.tei-c.org/ns/1.0" del="{normalize-space(string-join(./tei:del//text(), ''))}">
            <xsl:apply-templates select="./tei:add/child::node()"/>
        </subst>
    </xsl:template>

    <xsl:template match="tei:seg[@type = 'comment' and tei:orig and tei:note[@xml:id]]">
        <commented xmlns="https://sozinianer.de" key="{./tei:note/@xml:id}"> <!-- comment="{./tei:note//text()/normalize-space()}" -->
            <xsl:apply-templates select="./tei:orig/child::node()"/>
        </commented>
    </xsl:template>

    <!-- bibl[@type='ref'] zu bibl vereinfachen -->
    <xsl:template match="tei:bibl[@sameAs]">
        <xsl:element name="bibl" namespace="http://www.tei-c.org/ns/1.0">
            <xsl:attribute name="sameAs"><xsl:value-of select="replace(./@sameAs, 'zotero-2065617-', 'https://www.zotero.org/groups/2065617/sbw/items/')"/></xsl:attribute>
            <xsl:apply-templates select="./child::node()"/>
        </xsl:element>
    </xsl:template>

    <!-- rs[@type='ref'] zu bibl vereinfachen -->
    <xsl:template match="tei:rs[@type='source'][@key]">
        <xsl:element name="bibl" namespace="http://www.tei-c.org/ns/1.0">
            <xsl:attribute name="sameAs"><xsl:value-of select="replace(./@key, 'zotero-2065617-', 'https://www.zotero.org/groups/2065617/sbw/items/')"/></xsl:attribute>
            <xsl:apply-templates select="./child::node()"/>
        </xsl:element>
    </xsl:template>

    <!-- differenzieren zwischen ref für externe URLs und als Querverweis zu Sachanmerkungen -->
    <xsl:template match="tei:ref[not(matches(./@target, 'http'))]">
        <xsl:element name="rs" namespace="http://www.tei-c.org/ns/1.0">
            <xsl:attribute name="type">comment</xsl:attribute>
            <xsl:attribute name="key"><xsl:value-of select="./@target"/></xsl:attribute>
            <xsl:apply-templates select="./child::node()"/>
        </xsl:element>
    </xsl:template>

    <!-- this should also be removed from the framework and the source data -->
    <xsl:template match="tei:rs[@type = ('comet', 'letter', 'place')]/@key">
        <xsl:attribute name="key"><xsl:value-of select="replace(., '^#', '')"/></xsl:attribute>
    </xsl:template>

    <xsl:template match="tei:fw[@type = 'catch']"/>

    <!-- this template must not eat self-closing elements such as pb – test if attributes are present -->
    <xsl:template match="(tei:head|tei:opener|tei:closer|tei:dateline|tei:salute|tei:signed)[normalize-space() = '' and not(.//@*)]"/>

    <!--<xsl:template match="tei:div[@type='letter']">
        <xsl:apply-templates select="./*"/>
    </xsl:template>-->


    <xsl:template match="tei:pb[not(matches(@facs, '^http'))]"><pb xmlns="http://www.tei-c.org/ns/1.0" n="{./@n}"/></xsl:template>

    <!-- omit pb element if attribute n is not set -->
    <xsl:template match="tei:pb[not(@n)]"/>

</xsl:stylesheet>