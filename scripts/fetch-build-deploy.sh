#!/usr/bin/env bash

set -e

./scripts/_fetch.sh
./scripts/_build.sh
./scripts/_deploy.sh

say done with fetch build deploy
