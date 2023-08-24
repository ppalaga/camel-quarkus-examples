package org.acme.cxf.soap.interceptor.security;

/**
 */
import java.security.Principal;

import javax.security.auth.Subject;

import jakarta.inject.Inject;
import jakarta.servlet.ServletRequest;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.interceptor.security.DefaultSecurityContext;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.security.SecurityContext;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.auth.server.event.SecurityAuthenticationFailedEvent;

/**
 * @author Jochen Riedlinger (IT 270)
 *
 */
public abstract class BaseSubjectCreatingPolicyInterceptor<T extends Message> extends AbstractPhaseInterceptor<Message> {

    @Inject
    // definiert in io.quarkus.elytron.security.runtime.ElytronSecurityDomainManager
    SecurityDomain securityDomain;

    public BaseSubjectCreatingPolicyInterceptor(String phase) {
        super(phase);
    }

    /**
     * @param  securityDomainName Name einer in JBoss/Fuse kongigurierten Elytron Security Domain
     * @return                    Referenz auf die konfigurierte Elytron Security Domain
     */
    protected SecurityDomain getSecurityDomain() {
        return this.securityDomain;
    }

    protected SecurityContext createSecurityContext(String name, Subject subject) {
        return new DefaultSecurityContext(name, subject);
    }

    protected String getRemoteAddress(Message message) {
        String remoteAddr = null;
        if (message instanceof SoapMessage) {
            SoapMessage soapMessage = (SoapMessage) message;
            Object httpRequest = soapMessage.get("HTTP.REQUEST");
            if (httpRequest != null && (httpRequest instanceof ServletRequest)) {
                ServletRequest servletRequest = (ServletRequest) httpRequest;
                remoteAddr = servletRequest.getRemoteAddr();
            }
        }
        return remoteAddr;
    }

    protected void handleSecurityAuthenticationFailedEvent(SecurityDomain securityDomain, Principal userPrincipal,
            ServerAuthenticationContext context, Message message)
            throws IllegalArgumentException, RealmUnavailableException, IllegalStateException {

        //FIXME: was ist nun zu tun, damit Feature nicht verloren geht?
        //		SecurityAuthenticationFailedEvent secEvent = prepareSecurityAuthenticationFailedEvent(userPrincipal, context, message);
        //		// geht nicht wegen Sichtbarkeit
        ////		SecurityDomain.safeHandleSecurityEvent(securityDomain, secEvent);
        //		// deshalb mit diesem Trick
        //		SafeHandleSecurityEventWrapper.safeHandleSecurityEvent(securityDomain, secEvent);

    }

    protected SecurityAuthenticationFailedEvent prepareSecurityAuthenticationFailedEvent(Principal userPrincipal,
            ServerAuthenticationContext context, Message message)
            throws IllegalArgumentException, RealmUnavailableException, IllegalStateException {
        // https://issues.jboss.org/browse/ENTESB-9191?focusedCommentId=13624442&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-13624442
        // evtl. muss so geloggt werden:

        Principal principalForSecurityEvent = null;
        if (userPrincipal == null) {
            principalForSecurityEvent = prepareNoUserPseudoPrincipal(message);
        } else {
            principalForSecurityEvent = userPrincipal;
        }

        context.setAuthenticationName(principalForSecurityEvent.getName());

        SecurityAuthenticationFailedEvent secEvent = new SecurityAuthenticationFailedEvent(null, principalForSecurityEvent);
        return secEvent;
    }

    abstract Principal prepareNoUserPseudoPrincipal(Message message);

}
