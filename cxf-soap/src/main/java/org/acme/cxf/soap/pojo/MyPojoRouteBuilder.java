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
package org.acme.cxf.soap.pojo;

import java.util.HashMap;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.acme.cxf.soap.interceptor.security.SubjectCreatingSAMLPolicyInterceptor;
import org.acme.cxf.soap.pojo.service.ContactService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.cxf.ws.security.SecurityConstants;

/**
 * This class demonstrate how to expose a SOAP endpoint starting from java classes
 */
@ApplicationScoped
public class MyPojoRouteBuilder extends RouteBuilder {

    @Produces
    @SessionScoped
    @Named
    CxfEndpoint contact() {
        CxfEndpoint contactEndpoint = new CxfEndpoint();
        contactEndpoint.setWsdlURL("wsdl/ContactService.wsdl");
        contactEndpoint.setServiceClass(ContactService.class);
        contactEndpoint.setAddress("/contact");

        contactEndpoint.setProperties(new HashMap<>());

        // for "SAML Sender Vouches" Authentication
        Properties samlProps = new Properties();
        samlProps.put("org.apache.wss4j.crypto.provider", "org.apache.wss4j.common.crypto.Merlin");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.type", "pkcs12");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.file", "saml.p12");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.password", "Secret!");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.alias", "saml-key");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.private.password", "Secret!");
        samlProps.put("org.apache.wss4j.crypto.merlin.keystore.private.caching", "true");

        contactEndpoint.getProperties().put(SecurityConstants.SIGNATURE_PROPERTIES, samlProps);

        // contactEndpoint.getProperties().put(org.apache.cxf.ws.security.SecurityConstants.STORE_BYTES_IN_ATTACHMENT, "true");

        contactEndpoint.getInInterceptors().add(new SubjectCreatingSAMLPolicyInterceptor());

        contactEndpoint.getFeatures().add(new org.apache.cxf.ext.logging.LoggingFeature());

        return contactEndpoint;
    }

    @Override
    public void configure() throws Exception {
        from("cxf:bean:contact")
                .recipientList(simple("bean:inMemoryContactService?method=${header.operationName}"))
                .removeHeaders("*");
        ;
    }
}
