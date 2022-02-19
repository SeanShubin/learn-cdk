#!/usr/bin/env bash

set -e

./scripts/_teardown.sh
./scripts/_fetch.sh
./scripts/_build.sh
./scripts/_deploy.sh

say done with teardown fetch build deploy
