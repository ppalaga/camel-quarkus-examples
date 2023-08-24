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

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import org.acme.cxf.soap.pojo.service.Address;
import org.acme.cxf.soap.pojo.service.Contact;
import org.acme.cxf.soap.pojo.service.ContactService;
import org.acme.cxf.soap.pojo.service.ContactType;
import org.acme.cxf.soap.pojo.service.NoSuchContactException;
import org.acme.cxf.soap.security.SamlStandaloneCallbackHandler;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.security.SecurityConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PojoClientTest extends BaseTest {

    protected ContactService createCXFClient() {

        final URL serviceUrl = Thread.currentThread().getContextClassLoader().getResource("wsdl/ContactService.wsdl");
        final QName qName = new QName(ContactService.TARGET_NS, ContactService.class.getSimpleName());
        final Service service = Service.create(serviceUrl, qName);

        ContactService port = service.getPort(ContactService.class);
        BindingProvider bp = (BindingProvider) port;
        Map<String, Object> requestContext = bp.getRequestContext();

        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, getServerUrl() + "/cxf/services/contact");

        Properties samlProps = new Properties();
        samlProps.put("org.apache.wss4j.crypto.provider", "org.apache.wss4j.common.crypto.Merlin");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.type", "pkcs12");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.file", "saml.p12");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.password", "Secret!");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.alias", "saml-key");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.private.password", "Secret!");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.private.caching", "true");

        requestContext.put(SecurityConstants.SIGNATURE_PROPERTIES, samlProps);
        requestContext.put(SecurityConstants.SAML_CALLBACK_HANDLER, new SamlStandaloneCallbackHandler("TestUser"));

        requestContext.put(SecurityConstants.STORE_BYTES_IN_ATTACHMENT, false);

        List<Interceptor<? extends Message>> inInterceptors = ClientProxy.getClient(port).getInInterceptors();
        List<Interceptor<? extends Message>> outInterceptors = ClientProxy.getClient(port).getOutInterceptors();

        LoggingInInterceptor loggingInInterceptor = new LoggingInInterceptor();
        loggingInInterceptor.setPrettyLogging(true);
        inInterceptors.add(loggingInInterceptor);

        LoggingOutInterceptor loggingOutInterceptor = new LoggingOutInterceptor();
        loggingOutInterceptor.setPrettyLogging(true);
        outInterceptors.add(loggingOutInterceptor);

        return port;
    }

    protected static Contact createContact() {
        Contact contact = new Contact();
        contact.setName("Croway");
        contact.setType(ContactType.OTHER);
        Address address = new Address();
        address.setCity("Rome");
        address.setStreet("Test Street");
        contact.setAddress(address);

        return contact;
    }

    @Test
    public void testBasic() throws NoSuchContactException {
        ContactService cxfClient = createCXFClient();

        cxfClient.addContact(createContact());
        Assertions.assertSame(1, cxfClient.getContacts().getContacts().size(), "We should have one contact.");

        Assertions.assertNotNull(cxfClient.getContact("Croway"), "We haven't found contact.");

        Assertions.assertThrows(NoSuchContactException.class, () -> cxfClient.getContact("Non existent"));
    }
}
