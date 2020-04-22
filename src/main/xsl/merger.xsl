<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:math="http://www.w3.org/2005/xpath-functions/math"
  exclude-result-prefixes="#all"
  version="3.0">
  
  <xsl:param name="merge" as="xs:boolean" select="false()"/>
  <xsl:param name="documentToInclude" as="xs:anyURI?"/>
  
  <xsl:output method="xml" indent="true"/>

  <xsl:template match="/*">
    <xsl:copy>
      <xsl:if test="$merge">
        <xsl:variable name="documentUri" as="xs:anyURI" select="if(empty($documentToInclude)) then resolve-uri('doc2.xml',base-uri(.)) else $documentToInclude"/>
        <xsl:copy-of select="doc($documentUri)/*"/>
      </xsl:if>
    </xsl:copy>
  </xsl:template>
  
</xsl:stylesheet>