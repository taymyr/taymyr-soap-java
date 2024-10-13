package org.taymyr.lagom.soap.interceptor

import org.apache.cxf.interceptor.LoggingOutInterceptor
import org.apache.cxf.io.CacheAndWriteOutputStream
import org.apache.cxf.io.CachedOutputStream
import org.apache.cxf.io.CachedOutputStreamCallback
import org.apache.cxf.message.Message
import org.apache.cxf.phase.Phase.PRE_STREAM
import org.taymyr.lagom.soap.interceptor.AbstractLoggingShadowingInterceptor.Companion.shadowStringXml
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Wraps logging outcome callback for logging fields with shadowing
 */
class ShadowingLoggingOutInterceptor(
    shadowingSettings: List<ShadowingSettings>,
    maxSize: Int?,
) : AbstractLoggingShadowingInterceptor(phase = PRE_STREAM, shadowingSettings = shadowingSettings) {
    private val maxSize = maxSize ?: (1024 * 48)

    init {
        addAfter(LoggingOutInterceptor::class.java.name)
    }

    override fun handleMessage(message: Message) {
        val os = message.getContent(OutputStream::class.java) as CacheAndWriteOutputStream
        // Here we have one org.apache.cxf.interceptor.LoggingOutInterceptor.LoggingCallback that we are going to wrap
        val callbacks = os.callbacks.toMutableList().onEach {
            os.deregisterCallback(it)
        }
        message.exchange[SOAP_ACTION] = message.soapAction
        os.registerCallback(
            NameShadowingCallback(
                originalMessage = message,
                shadowingSettings = shadowingSettings,
                loggingCallback = callbacks,
                maxSize = maxSize
            )
        )
    }
}

class NameShadowingCallback(
    private val originalMessage: Message,
    private val loggingCallback: List<CachedOutputStreamCallback>,
    private val shadowingSettings: List<ShadowingSettings>,
    private val maxSize: Int,
) : CachedOutputStreamCallback {

    override fun onClose(cos: CachedOutputStream) {
        val sb = StringBuilder()
        cos.writeCacheTo(sb)
        val result = sb.toString()
        val baos = ByteArrayOutputStream(maxSize)
        baos.write(
            (
                shadowStringXml(
                    result,
                    shadowingSettings.firstOrNull { it.soapMethod == originalMessage.soapAction }
                ) { it.requestPatterns }
                ).toByteArray()
        )
        val shadowedCos = CacheAndWriteOutputStream(baos)
        shadowedCos.resetOut(baos, true)
        loggingCallback.forEach {
            it.onClose(shadowedCos)
        }
    }

    override fun onFlush(os: CachedOutputStream?) {}
}
