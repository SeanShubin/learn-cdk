#!/usr/bin/env bash

set -e

pushd ../condorcet-backend/
mvn clean
mvn verify -DskipTests
popd

./scripts/_compose-s3-data.sh
./scripts/_teardown.sh
./scripts/_build.sh
./scripts/_deploy.sh

say done with debug build
