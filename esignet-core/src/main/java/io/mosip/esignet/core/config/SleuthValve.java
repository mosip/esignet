package io.mosip.esignet.core.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import java.io.IOException;

@Component
public class SleuthValve extends ValveBase {

    private final Tracer tracer;

    public SleuthValve(Tracer tracer) {
        this.tracer = tracer;
        setAsyncSupported(true);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        enrichWithSleuthHeaderWhenMissing(request);

        Valve next = getNext();
        if (next != null) {
            next.invoke(request, response);
        }
    }

    private void enrichWithSleuthHeaderWhenMissing(final Request request) {
        if (request.getHeader("b3") == null && request.getHeader("X-B3-TraceId") == null) {
            Span span = tracer.nextSpan().name("tomcat-incoming").start();

            MimeHeaders headers = request.getCoyoteRequest().getMimeHeaders();
            addHeader(headers, "X-B3-TraceId", span.context().traceId());
            addHeader(headers, "X-B3-SpanId",  span.context().spanId());

            span.end();
        }
    }

    private static void addHeader(MimeHeaders mimeHeaders, String traceIdName, String value) {
        MessageBytes messageBytes = mimeHeaders.addValue(traceIdName);
        messageBytes.setString(value);
    }
}
