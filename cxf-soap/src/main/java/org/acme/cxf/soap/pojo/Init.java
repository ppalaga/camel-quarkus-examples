package org.acme.cxf.soap.pojo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HTTPConduitConfigurer;

@ApplicationScoped
public class Init {
    void onStart(@Observes StartupEvent ev) {

        HTTPConduitConfigurer httpConduitConfigurer = new HTTPConduitConfigurer() {
            public void configure(String name, String address, HTTPConduit c) {

                TrustManager[] trustManagers;

                try {
                    KeyStore trustStore = KeyStore.getInstance("pkcs12");
                    try (InputStream is = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream("saml.p12")) {
                        trustStore.load(is, "Secret!".toCharArray());

                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init(trustStore);
                        trustManagers = tmf.getTrustManagers();
                    }
                } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                    throw new RuntimeException(e);
                }

                TLSClientParameters tlsCP = new TLSClientParameters();
                tlsCP.setTrustManagers(trustManagers);

                // other TLS/SSL configuration like setting up TrustManagers
                // in case of "localhost" the certname does not match the hostname, so ignore it
                tlsCP.setDisableCNCheck(isLocalhost(c));
                tlsCP.setUseHttpsURLConnectionDefaultSslSocketFactory(false);

                c.setTlsClientParameters(tlsCP);

            }
        };

        final Bus bus = BusFactory.getThreadDefaultBus();
        bus.setExtension(httpConduitConfigurer, HTTPConduitConfigurer.class);
    }

    protected static boolean isLocalhost(HTTPConduit httpConduit) {
        boolean result = false;
        try {
            String address = httpConduit.getAddress();
            URL url = new URL(address);
            String host = url.getHost();
            if (isLocalhost(host)) {
                result = true;
            }
        } catch (MalformedURLException e) {
            // egal, dann bleibt's halt "false"
        }
        return result;
    }

    protected static boolean isLocalhost(String hostname) {
        return "localhost".equalsIgnoreCase(hostname) || "127.0.0.1".equals(hostname);
    }
}
