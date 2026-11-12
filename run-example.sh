#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/marketplace-image-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" learning.marketplace.MarketplaceBatchExample "$@"
