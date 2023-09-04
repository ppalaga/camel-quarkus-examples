package org.acme.cxf.soap.pojo;

import java.io.IOException;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.apache.cxf.transport.http.URLConnectionHTTPConduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

@ApplicationScoped
public class Init {
    void onStart(@Observes StartupEvent ev) {
        final Bus bus = BusFactory.getThreadDefaultBus();
        bus.setExtension(new URLConnectionHTTPConduitFactory(), HTTPConduitFactory.class);
    }

    public static class URLConnectionHTTPConduitFactory implements HTTPConduitFactory {
        @Override
        public HTTPConduit createConduit(HTTPTransportFactory f, Bus bus, EndpointInfo endpointInfo,
                EndpointReferenceType target)
                throws IOException {
            return new URLConnectionHTTPConduit(bus, endpointInfo, target);
        }
    }
}
