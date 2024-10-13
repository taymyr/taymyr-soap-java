package org.taymyr.lagom.soap.interceptor

import org.apache.cxf.helpers.XPathUtils
import org.apache.cxf.message.Message
import org.apache.cxf.message.Message.PROTOCOL_HEADERS
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD
import javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA
import javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET
import javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants.NODE
import javax.xml.xpath.XPathConstants.NODESET

const val SOAP_ACTION = "org.taymyr.lagom.soap.interceptor.soapAction"

@Suppress("unchecked_cast")
val Message.soapAction: String? get() = (this[PROTOCOL_HEADERS] as Map<String, List<String>>)["SOAPAction"]?.firstOrNull()

private val DOCUMENT_BUILDER: DocumentBuilder = DocumentBuilderFactory.newInstance().apply {
    setAttribute(ACCESS_EXTERNAL_DTD, "")
    setAttribute(ACCESS_EXTERNAL_SCHEMA, "")
    setFeature(FEATURE_SECURE_PROCESSING, true)
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
}.newDocumentBuilder()

private val TRANSFORMER = TransformerFactory.newInstance().apply {
    setAttribute(ACCESS_EXTERNAL_DTD, "")
    setAttribute(ACCESS_EXTERNAL_STYLESHEET, "")
}.newTransformer()

private val xPathUtils: XPathUtils = XPathUtils()

abstract class AbstractLoggingShadowingInterceptor(
    phase: String,
    protected val shadowingSettings: List<ShadowingSettings>
) : AbstractPhaseInterceptor<Message>(phase) {

    companion object {
        fun shadowStringXml(
            sourceXml: String,
            shadowingConfig: ShadowingSettings?,
            configSelector: (ShadowingSettings) -> List<String>
        ): String =
            if (shadowingConfig != null) {
                replaceAllEntrance(
                    xmlString = sourceXml,
                    replacingValue = shadowingConfig.replacingSymbols,
                    regexps = configSelector(shadowingConfig)
                )
            } else sourceXml

        private tailrec fun replaceAllEntrance(
            xmlString: String,
            regexps: List<String>,
            replacingValue: String
        ): String =
            if (regexps.isNotEmpty())
                replaceAllEntrance(
                    replaceXml(xmlString, regexps.first(), replacingValue),
                    regexps.drop(1),
                    replacingValue
                )
            else
                xmlString

        private fun replaceXml(xmlString: String, xmlPathExp: String, replacingValue: String): String =
            runCatching {
                val doc = DOCUMENT_BUILDER.parse(ByteArrayInputStream(xmlString.toByteArray()))
                // node and nodes are mutual exclusive in common
                val node = xPathUtils.getValue(xmlPathExp, doc, NODE) as? Element
                val nodes = xPathUtils.getValue(xmlPathExp, doc, NODESET) as? NodeList
                when {
                    nodes != null -> {
                        (0..nodes.length).mapNotNull { nodes.item(it) as? Element }.forEach {
                            replaceNode(replacingValue = replacingValue, sourceDocument = doc, foundNode = it)
                        }
                        getXmlWithReplacing(doc)
                    }

                    (node != null) -> {
                        replaceNode(replacingValue = replacingValue, sourceDocument = doc, foundNode = node)
                        getXmlWithReplacing(sourceDocument = doc)
                    }

                    else -> xmlString
                }
            }.recover { xmlString }
                .getOrThrow()

        private fun replaceNode(replacingValue: String, sourceDocument: Document, foundNode: Node) {
            val fieldName = foundNode.nodeName
            val fragmentDoc =
                DOCUMENT_BUILDER.parse(ByteArrayInputStream("<$fieldName>$replacingValue</$fieldName>".toByteArray()))
            val injectedNode = sourceDocument.adoptNode(fragmentDoc.firstChild)
            val parentNode = foundNode.parentNode
            parentNode.removeChild(foundNode)
            parentNode.appendChild(injectedNode)
        }

        private fun getXmlWithReplacing(sourceDocument: Document): String {
            val result = StreamResult(StringWriter())
            TRANSFORMER.transform(DOMSource(sourceDocument), result)
            return result.writer.toString()
        }
    }
}
