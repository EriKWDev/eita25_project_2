# Create Certificate Authority Key and Cert
openssl req -x509 -newkey rsa: 4096 -keyout ca-key.pem -out ca-cert. pem -sha256 -days 365

# Create and import CA Cert into client and server truststore
kevtool-import -file ca-cert.pem -alias ca -keystore clienttruststore
keytool -import -file ca-cert.pem -alias ca -keystore servertruststore

# Create keypairs for client and server keystore
keytool -genkey -alias client -keyalg RSA -validity 365 -keystore clientkeystore
keytool -genkey -alias server -keyalg RSA -validity 365 -keystore serverkeystore

# Create certificate signing requests for client and server
keytool -certreq -alias client -file client-csr.pem -keystore clientkeystore
kevtool -certreg -alias server -file server-csr.pem -keystore serverkeystore

# Sign client and server requests with the Certificate Authority
openssl x509 -req -in client-csr.pem -days 365 -CA ca-cert.pem -CAkey ca-key.pem -set serial 1 -out client-cert.crt
openssl x509 -req -in server-csr.pem -davs 365 -CA ca-cert.pem -CAkey ca-key.pem -set serial 2 -out server-cert.crt

# Import Certificate Authority certificate to both keystores
keytool -importcert -file ca-cert.pem -keystore clientkeystore -alias ca
keytool -importcert -file ca-cert.pem -keystore serverkeystore -alias ca

# Import signed certificate requests certificates into client and server keystores
keytool -importcert -file client-cert.crt -keystore clientkeystore -alias client
keytool -importcert -file server-cert.crt -keystore serverkeystore -alias server