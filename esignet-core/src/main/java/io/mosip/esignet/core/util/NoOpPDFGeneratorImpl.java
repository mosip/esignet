package io.mosip.esignet.core.util;

import io.mosip.kernel.core.keymanager.model.CertificateEntry;
import io.mosip.kernel.core.pdfgenerator.model.Rectangle;
import io.mosip.kernel.core.pdfgenerator.spi.PDFGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.List;

@Slf4j
@Component
public class NoOpPDFGeneratorImpl implements PDFGenerator {
    @Override
    public OutputStream generate(InputStream inputStream) throws IOException {
        log.warn("NoOpGenerator generate invoked");
        return null;
    }

    @Override
    public OutputStream generate(String s) throws IOException {
        log.warn("NoOpGenerator generate invoked");
        return null;
    }

    @Override
    public void generate(String s, String s1, String s2) throws IOException {
        log.warn("NoOpGenerator generate invoked");
    }

    @Override
    public OutputStream generate(InputStream inputStream, String s) throws IOException {
        log.warn("NoOpGenerator generate invoked");
        return null;
    }

    @Override
    public byte[] asPDF(List<BufferedImage> list) throws IOException {
        log.warn("NoOpGenerator asPDF invoked");
        return new byte[0];
    }

    @Override
    public byte[] mergePDF(List<URL> list) throws IOException {
        log.warn("NoOpGenerator mergePDF invoked");
        return new byte[0];
    }

    @Override
    public OutputStream signAndEncryptPDF(byte[] bytes, Rectangle rectangle, String s, int i, Provider provider, CertificateEntry<X509Certificate, PrivateKey> certificateEntry, String s1) throws IOException, GeneralSecurityException {
        log.warn("NoOpGenerator signAndEncryptPDF invoked");
        return null;
    }
}
