#!/usr/bin/env bash

set -e

./scripts/_compose-s3-data.sh
./scripts/_build.sh

say done with build
