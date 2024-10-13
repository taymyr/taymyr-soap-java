package org.taymyr.lagom.soap.interceptor

import org.apache.cxf.helpers.IOUtils
import org.apache.cxf.interceptor.LoggingInInterceptor
import org.apache.cxf.io.CachedOutputStream
import org.apache.cxf.message.Message
import org.apache.cxf.phase.Phase.RECEIVE
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import kotlin.Int.Companion.MAX_VALUE

private const val SOURCE_INPUT_STREAM = "org.taymyr.lagom.soap.interceptor.sourceInputStream"

/**
 * Wraps logging income inputstream to overwrite data before logging
 */
class PreShadowingLoggingInInterceptor(
    shadowingSettings: List<ShadowingSettings>,
) : AbstractLoggingShadowingInterceptor(phase = RECEIVE, shadowingSettings = shadowingSettings) {

    init {
        addBefore(LoggingInInterceptor::class.java.name)
    }

    override fun handleMessage(message: Message) {
        val soapAction = message.exchange[SOAP_ACTION]
        if (soapAction != null) {
            val bis = message.getContent(InputStream::class.java)
            val bos = CachedOutputStream()
            // copy all to process all xml string
            IOUtils.copyAtLeast(bis, bos, MAX_VALUE)
            bos.flush()
            val result = bos.inputStream.toString()
            message[SOURCE_INPUT_STREAM] = ByteArrayInputStream(result.toByteArray())
            val bais = ByteArrayInputStream(
                (
                    shadowStringXml(
                        sourceXml = result,
                        shadowingConfig = shadowingSettings.firstOrNull { it.soapMethod == soapAction }
                    ) { it.responsePatterns }
                    ).toByteArray()
            )
            message.setContent(InputStream::class.java, bais)
        }
    }
}

/**
 * Wraps logging income inputstream to overwrite data after logging
 */
class PostShadowingLoggingInInterceptor(
    shadowingSettings: List<ShadowingSettings>,
    maxSize: Int?
) : AbstractLoggingShadowingInterceptor(phase = RECEIVE, shadowingSettings = shadowingSettings) {
    private val maxSize = maxSize ?: (1024 * 48)
    init {
        addAfter(LoggingInInterceptor::class.java.name)
    }

    override fun handleMessage(message: Message) {
        val soapAction = message.exchange[SOAP_ACTION]
        if (soapAction != null) {
            val bis = message[SOURCE_INPUT_STREAM] as InputStream
            val bos = CachedOutputStream()
            IOUtils.copyAtLeast(bis, bos, if (maxSize == -1) MAX_VALUE else maxSize)
            bos.flush()
            val bais = SequenceInputStream(bos.getInputStream(), bis)
            message.setContent(InputStream::class.java, bais)
        }
    }
}
