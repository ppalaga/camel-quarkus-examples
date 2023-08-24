package org.acme.cxf.soap.interceptor.security;

import java.net.HttpURLConnection;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.xml.namespace.QName;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.apache.cxf.common.security.SimplePrincipal;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.security.DefaultSecurityContext;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.security.SecurityContext;
import org.apache.wss4j.common.principal.SAMLTokenPrincipal;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.dom.handler.WSHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Roles;

/**
 * This is for use in a Wildfly-Camel implementation on Fuse (on EAP) / Wildfly-Camel!
 *
 * CAUTION: Do NOT confuse this interceptor with
 * <code>de.lbank.framework.ws.server.security.saml.SubjectCreatingSAMLPolicyInterceptor</code> which is used in
 * "normal" JAX-WS based applications on JBoss EAP.
 *
 * A CXF-Interceptor for SAML-Sender-Vouches Authentication that integrates with the (new) Elytron security subsystem of
 * Wildfly/JBoss EAP.
 *
 * The idea is inspired by <code>org.jboss.wsf.stack.cxf.security.authentication.SubjectCreatingPolicyInterceptor</code>
 * and
 * <code>org.jboss.as.webservices.security.ElytronSecurityDomainContextImpl</code>
 *
 *
 * @author Jochen Riedlinger (IT 270)
 */
@ApplicationScoped
public class SubjectCreatingSAMLPolicyInterceptor extends BaseSubjectCreatingPolicyInterceptor<Message> {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @Inject
    RoutingContext routingContext;

    Instance<SecurityDomain> currentSecurityDomainInstance = CDI.current().select(SecurityDomain.class);

    Instance<CurrentIdentityAssociation> identityAssociationInstance = CDI.current().select(CurrentIdentityAssociation.class);

    @Inject
    SecurityIdentity identity;

    public SubjectCreatingSAMLPolicyInterceptor() {
        super(Phase.PRE_INVOKE);
    }

    @Override
    public void handleMessage(Message message) throws Fault {

        SecurityDomain currentSecurityDomain = currentSecurityDomainInstance.get();

        //		Endpoint ep = message.getExchange().get(Endpoint.class);

        this.validateSecurityContext(message, currentSecurityDomain);

        List<WSHandlerResult> myList = this.getWSHandlerResultListWithValidation(currentSecurityDomain, message);
        for (WSHandlerResult wsHandlerResult : myList) {

            // find the security engine result for SAML authentication
            WSSecurityEngineResult wsSecurityEngineResult = this
                    .findSecurityEngineResultForSamlAuthenticationIn(wsHandlerResult.getResults());

            if (wsSecurityEngineResult != null) {
                Boolean validatedToken = (Boolean) wsSecurityEngineResult.get(WSSecurityEngineResult.TAG_VALIDATED_TOKEN);
                Principal p = (Principal) wsSecurityEngineResult.get(WSSecurityEngineResult.TAG_PRINCIPAL);

                try {

                    SecurityIdentity fromSamlTokenCVreatedIdentity = this.authenticateInWildflyElytron(currentSecurityDomain,
                            p.getName(), validatedToken.toString());

                    CurrentIdentityAssociation currentIdentityAssociation = this.identityAssociationInstance.get();
                    currentIdentityAssociation.setIdentity(fromSamlTokenCVreatedIdentity);

                    // checks if wildfly security subsystem returned an identity with principal
                    this.validateSecurityIdentity(fromSamlTokenCVreatedIdentity, currentSecurityDomain, p, message);

                    // put security context to the cxf message
                    if (fromSamlTokenCVreatedIdentity != null && fromSamlTokenCVreatedIdentity.getPrincipal() != null) {
                        String name = fromSamlTokenCVreatedIdentity.getPrincipal().getName();
                        Subject subject = new Subject();
                        // The first principal added must be the security identity principal
                        // as logic in both CXF and JBoss WS look for the first non-Group principal
                        subject.getPrincipals().add(fromSamlTokenCVreatedIdentity.getPrincipal());

                        Set<Principal> principals = subject.getPrincipals();
                        Set<String> roles = fromSamlTokenCVreatedIdentity.getRoles();
                        roles.forEach(roleName -> {
                            principals.add(new NamePrincipal(roleName));
                        });

                        message.put(SecurityContext.class, createSecurityContext(name, subject));
                    }
                } catch (Fault fault) {
                    throw fault;
                } catch (Exception e) {
                    // auditing
                    SecurityException se = new SecurityException(e); // throw SecurityException when something in authentication fails
                    this.handleSecurityException(se, currentSecurityDomain, p, message);
                }

            } else {
                SecurityException se = new SecurityException("No security engine result for SAML authentication found.");
                this.handleSecurityException(se, currentSecurityDomain, null, message);
            }
        }
    }

    /**
     * Finds the security engine result for SAML authentication from the given list of WSSecurityEngineResults.
     *
     * @param  results
     * @return         the WSSecurityEngineResult for SAML or null if no object in the list matches the criteria
     */
    private WSSecurityEngineResult findSecurityEngineResultForSamlAuthenticationIn(List<WSSecurityEngineResult> results) {
        for (WSSecurityEngineResult wsSecurityEngineResult : results) {
            Principal p = (Principal) wsSecurityEngineResult.get(WSSecurityEngineResult.TAG_PRINCIPAL);
            if (p instanceof SAMLTokenPrincipal) {
                return wsSecurityEngineResult;
            }
        }
        return null;
    }

    /**
     * Retrieves the list of WS handler result objects from the message (WSHandlerConstants.RECV_RESULTS)
     *
     * @param  currentSecurityDomain
     * @param  message
     * @return                       the list of WS handler result objects
     * @throws Fault                 if list does not exist or it is empty
     */
    private List<WSHandlerResult> getWSHandlerResultListWithValidation(SecurityDomain currentSecurityDomain, Message message) {
        List<WSHandlerResult> myList = (List<WSHandlerResult>) message.get(WSHandlerConstants.RECV_RESULTS);
        if (myList == null || myList.isEmpty()) {
            SecurityException se = new SecurityException("List of WS Handler results was empty.");
            this.handleSecurityException(se, currentSecurityDomain, null, message);
        }

        return myList;
    }

    // // siehe:
    // // https://issues.jboss.org/browse/ENTESB-9191?focusedCommentId=13624442&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-13624442
    // protected Principal getPrincipal(Principal originalPrincipal, Subject subject) {
    // 	Principal[] ps = subject.getPrincipals().toArray(new Principal[0]);
    // 	for (int i = 0; i < ps.length; i++) {
    // 		if (ps != null && !(ps[i] instanceof Group)) {
    // 			return ps[i];
    // 		}
    // 	}
    // 	return originalPrincipal;
    // }
    protected Principal getPrincipal(Principal originalPrincipal, Subject subject) {
        Principal[] ps = subject.getPrincipals().toArray(new Principal[subject.getPrincipals().size()]);
        if (ps != null && ps.length > 0
                && !(DefaultSecurityContext.isGroupPrincipal(ps[0]))) {
            return ps[0];
        }
        return originalPrincipal;
    }

    /**
     * Creates an authentication context with given user name for the wildfly security domain and performs a 'pseudo'
     * authorization. This is necessary to get
     * the wildfly security subsystem properly initialized with security information.
     *
     * @param  securityDomain
     * @param  username
     * @param  password
     * @return
     */
    private SecurityIdentity authenticateInWildflyElytron(final SecurityDomain securityDomain, final String username,
            final String password) {

        try (ServerAuthenticationContext context = securityDomain.createNewAuthenticationContext()) {
            try {
                context.setAuthenticationName(username);
                if (Boolean.parseBoolean(password)) {
                    if (context.authorize()) {
                        context.succeed();
                        org.wildfly.security.auth.server.SecurityIdentity authorizedIdentity = context.getAuthorizedIdentity();

                        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
                        builder.setPrincipal(authorizedIdentity.getPrincipal());
                        //TODO: Rollen ...und noch mehr?!?!?!?
                        Roles roles = authorizedIdentity.getRoles();
                        Set<String> roleSet = new HashSet();
                        roles.forEach(roleSet::add);
                        builder.addRoles(roleSet);

                        return builder.build();
                    } else {
                        context.fail();
                    }
                } else {
                    context.fail();
                }
            } catch (IllegalArgumentException | IllegalStateException | RealmUnavailableException e) {
                context.fail();
                throw new RuntimeException(e);
            } finally {
                if (!context.isDone()) {
                    context.fail(); // prevent leaks of RealmIdentity instances
                }
            }
        }
        return null;
    }

    /**
     * Checks if the given securityIdentity contains a user principal
     *
     * @param  securityIdentity
     * @param  currentSecurityDomain
     * @param  principal
     * @param  message
     * @throws Fault                 if the given securtyIdentity does not contain user principal
     */
    private void validateSecurityIdentity(SecurityIdentity securityIdentity, SecurityDomain currentSecurityDomain,
            Principal principal, Message message) {
        if (securityIdentity == null || securityIdentity.getPrincipal() == null) {
            SecurityException se = new SecurityException("The given securityIdentity does not contain the user principal");
            this.handleSecurityException(se, currentSecurityDomain, principal, message);
        }
    }

    /**
     * Checks if the message contains a security context with user principal
     *
     * @param  message
     * @param  currentSecurityDomain
     * @throws Fault                 if no security context exists or it does not contain user principal
     */
    private void validateSecurityContext(Message message, SecurityDomain currentSecurityDomain) {
        SecurityContext context = message.get(SecurityContext.class);
        if (context == null || context.getUserPrincipal() == null) {
            SecurityException se = new SecurityException(
                    "No SAML Principal available on current Message in " + this.getClass().getName());
            this.handleSecurityException(se, currentSecurityDomain, null, message);
        }
    }

    protected void handleSecurityException(SecurityException se, SecurityDomain securityDomain, Principal userPrincipal,
            Message message) {
        // auditing
        this.failSamlLogin(securityDomain, userPrincipal, message);

        Fault fault = new Fault(se);
        fault.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED);
        fault.setFaultCode(new QName(String.valueOf(HttpURLConnection.HTTP_UNAUTHORIZED)));
        throw fault;
    }

    /*
     * Sorgt dafür, dass das Audit-Log bei dieser Login Variante auch im "fail"-Fall einen Eintrag bekommt
     */
    protected void failSamlLogin(SecurityDomain securityDomain, Principal userPrincipal, Message message) {
        try (ServerAuthenticationContext context = securityDomain.createNewAuthenticationContext()) {
            try {

                handleSecurityAuthenticationFailedEvent(securityDomain, userPrincipal, context, message);

            } catch (Exception e) {

            } finally {
                context.fail();
            }
        }
    }

    protected Principal prepareNoUserPseudoPrincipal(Message message) {
        String remoteAddr = getRemoteAddress(message);
        String noUser = "KEIN USERNAME aus SAML Mechanismus! WS-Request kam von: " + remoteAddr;
        return new SimplePrincipal(noUser);
    }

}
