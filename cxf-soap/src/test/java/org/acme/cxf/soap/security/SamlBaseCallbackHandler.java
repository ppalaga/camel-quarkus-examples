/**
 *
 */
package org.acme.cxf.soap.security;

import java.io.IOException;
import java.util.Collections;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.wss4j.common.saml.SAMLCallback;
import org.apache.wss4j.common.saml.bean.AuthenticationStatementBean;
import org.apache.wss4j.common.saml.bean.SubjectBean;
import org.apache.wss4j.common.saml.bean.Version;
import org.apache.wss4j.common.saml.builder.SAML1Constants;

/**
 * @author Jochen Riedlinger
 *
 */
public abstract class SamlBaseCallbackHandler implements CallbackHandler {

    /* (non-Javadoc)
     * @see javax.security.auth.callback.CallbackHandler#handle(javax.security.auth.callback.Callback[])
     */
    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof SAMLCallback) {
                SAMLCallback callback = (SAMLCallback) callbacks[i];

                callback.setSamlVersion(Version.SAML_11);
                //				callback.setSamlVersion(Version.SAML_20);

                callback.setIssuer(""); //FIXME

                String subjectName = this.getAuthPrincipalName();

                String subjectQualifier = "";

                String subjectConfMethod = SAML1Constants.CONF_SENDER_VOUCHES;
                //				String subjectConfMethod = SAML2Constants.CONF_SENDER_VOUCHES;

                SubjectBean subjectBean = new SubjectBean(subjectName, subjectQualifier, subjectConfMethod);

                callback.setSubject(subjectBean);

                AuthenticationStatementBean authBean = new AuthenticationStatementBean();
                authBean.setAuthenticationMethod(SAML1Constants.AUTH_METHOD_UNSPECIFIED);
                //				authBean.setAuthenticationMethod(SAML2Constants.ATTRNAME_FORMAT_UNSPECIFIED);
                authBean.setSubject(subjectBean);

                callback.setAuthenticationStatementData(Collections.singletonList(authBean));

            }
        }
    }

    protected abstract String getAuthPrincipalName();

}
