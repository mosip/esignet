package io.mosip.esignet.core.config;

import brave.Span;
import brave.Tracer;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import java.io.IOException;

@Component
public class SleuthValve extends ValveBase {

    private final Tracer tracer;

    SleuthValve(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        enrichWithSleuthHeaderWhenMissing(tracer, request);

        Valve next = getNext();
        if (null == next) {
            // no next valve
            return;
        }
        next.invoke(request, response);
    }

    private static void enrichWithSleuthHeaderWhenMissing(final Tracer tracer, final Request request) {
        String header = request.getHeader("X-B3-TraceId");

        if (null == header) {
            org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
            MimeHeaders mimeHeaders = coyoteRequest.getMimeHeaders();

            Span span = tracer.newTrace();

            addHeader(mimeHeaders, "X-B3-TraceId", span.context().traceIdString());
            addHeader(mimeHeaders, "X-B3-SpanId", span.context().traceIdString());
        }
    }

    private static void addHeader(MimeHeaders mimeHeaders, String traceIdName, String value) {
        MessageBytes messageBytes = mimeHeaders.addValue(traceIdName);
        messageBytes.setString(value);
    }
}
