/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.cxf.soap;

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

import io.quarkus.runtime.LaunchMode;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class BaseTest {
    // protected String getServerUrl() {
    //     Config config = ConfigProvider.getConfig();
    //     final int port = LaunchMode.current().equals(LaunchMode.TEST) ? config.getValue("quarkus.http.test-port", Integer.class)
    //             : config.getValue("quarkus.http.port", Integer.class);
    //     return String.format("http://localhost:%d", port);
    // }
    protected String getServerUrl() {
        Config config = ConfigProvider.getConfig();
        final int port = LaunchMode.current().equals(LaunchMode.TEST)
                ? config.getValue("quarkus.http.test-ssl-port", Integer.class)
                : config.getValue("quarkus.http.ssl-port", Integer.class);
        return String.format("https://localhost:%d", port);
    }

    protected <T> void initTLS(T wsPort) {

        TrustManager[] trustManagers;

        try {
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("saml.p12");
            trustStore.load(is, "Secret!".toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }

        HTTPConduit httpConduit = (HTTPConduit) ClientProxy.getClient(wsPort).getConduit();
        TLSClientParameters tlsCP = new TLSClientParameters();

        tlsCP.setTrustManagers(trustManagers);

        // other TLS/SSL configuration like setting up TrustManagers
        // in case of "localhost" the certname does not match the hostname, so ignore it
        tlsCP.setDisableCNCheck(isLocalhost(httpConduit));
        tlsCP.setUseHttpsURLConnectionDefaultSslSocketFactory(false);

        httpConduit.setTlsClientParameters(tlsCP);
    }

    protected boolean isLocalhost(HTTPConduit httpConduit) {
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

    protected boolean isLocalhost(String hostname) {
        return "localhost".equalsIgnoreCase(hostname) || "127.0.0.1".equals(hostname);
    }
}
