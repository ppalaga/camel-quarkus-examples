
set KEYSTORE=saml.p12

set KEYSTORE_PWD=Secret!
set PRIVATE_KEY_PWD=%KEYSTORE_PWD%

set ALIAS=saml-key

set DNAME="CN=saml,OU=ORG,O=ACME,L=Karlsruhe,ST=Baden-Wuerttemberg,C=BW"



keytool -genkeypair -noprompt -alias %ALIAS% -keyalg RSA -keysize 2048 -keystore %KEYSTORE% -storetype pkcs12 -dname %DNAME% -v -keypass %PRIVATE_KEY_PWD% -storepass %KEYSTORE_PWD% -validity 3650
