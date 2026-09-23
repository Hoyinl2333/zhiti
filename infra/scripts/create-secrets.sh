#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
secrets="$root/.secrets"
android_raw="$root/android/app/src/main/res/raw"
mkdir -p "$secrets" "$android_raw"
umask 077

if [[ ! -f "$secrets/ca.key" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$secrets/ca.key"
  openssl req -x509 -new -sha256 -key "$secrets/ca.key" -out "$secrets/ca.crt" -days 3650 \
    -subj "/CN=Zhiti Private CA/O=Zhiti"
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$secrets/server.key"
openssl req -new -sha256 -key "$secrets/server.key" -out "$secrets/server.csr" \
  -subj "/CN=43.136.39.211/O=Zhiti"
openssl x509 -req -in "$secrets/server.csr" -CA "$secrets/ca.crt" -CAkey "$secrets/ca.key" \
  -CAcreateserial -out "$secrets/server.crt" -days 825 \
  -extfile <(printf 'subjectAltName=IP:43.136.39.211\nextendedKeyUsage=serverAuth\nkeyUsage=digitalSignature,keyEncipherment')

if [[ ! -f "$secrets/content-ed25519-private.pem" ]]; then
  openssl genpkey -algorithm ED25519 -out "$secrets/content-ed25519-private.pem"
  openssl pkey -in "$secrets/content-ed25519-private.pem" -pubout \
    -out "$secrets/content-ed25519-public.pem"
fi

cp "$secrets/ca.crt" "$android_raw/zhiti_ca.crt"
cp "$secrets/content-ed25519-public.pem" "$android_raw/content_signing_public.pem"

if [[ ! -f "$secrets/zhiti-release.jks" ]]; then
  password="$(openssl rand -base64 36 | tr -d '/+=' | cut -c1-32)"
  keytool -genkeypair -v -keystore "$secrets/zhiti-release.jks" -alias zhiti \
    -keyalg RSA -keysize 4096 -validity 10000 -storepass "$password" -keypass "$password" \
    -dname "CN=Zhiti, O=Xiaoyunduo, C=CN"
  printf 'ZHITI_KEYSTORE=%s\nZHITI_KEYSTORE_PASSWORD=%s\nZHITI_KEY_ALIAS=zhiti\nZHITI_KEY_PASSWORD=%s\n' \
    "$secrets/zhiti-release.jks" "$password" "$password" > "$secrets/release.env"
fi

if [[ ! -f "$secrets/server.env" ]]; then
  printf 'ZHITI_PEPPER=%s\n' "$(openssl rand -hex 32)" > "$secrets/server.env"
fi

echo "Secrets ready in $secrets"
