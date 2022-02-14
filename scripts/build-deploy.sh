#!/usr/bin/env bash

set -e

./scripts/_compose-s3-data.sh
./scripts/_build.sh
./scripts/_deploy.sh

say done with build deploy
