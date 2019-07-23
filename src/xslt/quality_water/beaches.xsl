<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" doctype-public="XSLT-compat" omit-xml-declaration="yes" encoding="UTF-8" indent="yes"/>
    <xsl:template match="*">
        <xsl:text>{"error": </xsl:text>
        <xsl:value-of select="error"/>
        <xsl:text>, "errorDescription": "</xsl:text>
        <xsl:value-of select="descError"/>
        <xsl:text>", "points": [</xsl:text>
        <xsl:call-template name="gpoints"/>
        <xsl:text>]}</xsl:text>
    </xsl:template>

    <xsl:template name="gpoints">
        <xsl:for-each select="gpoints/gpoint">
        	<xsl:text>{"id": </xsl:text>
            <xsl:value-of select="./@id" />
            <xsl:text>, "latitude": </xsl:text>
            <xsl:apply-templates select="./@lat" />
        	<xsl:text>, "longitude": </xsl:text>
            <xsl:apply-templates select="./@lng" />
            <xsl:text>}</xsl:text>
        	<xsl:if test="last() > position()">
        	 	<xsl:text>, </xsl:text>
        	</xsl:if>
        </xsl:for-each>
    </xsl:template>

</xsl:stylesheet>