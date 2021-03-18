#!/usr/bin/env bash
mkdir ci/deploy
echo $BINTRAY_USER

openssl version
openssl aes-256-cbc -pass pass:$GPG_ENCPHRASE -in ci/pubring.gpg.enc -out ci/deploy/pubring.gpg -pbkdf2 -d
openssl aes-256-cbc -pass pass:$GPG_ENCPHRASE -in ci/secring.gpg.enc -out ci/deploy/secring.gpg -pbkdf2 -d
gpg --fast-import ci/deploy/pubring.gpg
gpg --fast-import ci/deploy/secring.gpg

# delete decrypted keys
rm -rf ci/deploy
mvn clean install -Dgpg.skip=true