#!/usr/bin/env bash

set -e

./scripts/_purge.sh
./scripts/_fetch.sh
./scripts/_build.sh
./scripts/_deploy.sh

say done with purge fetch build deploy
