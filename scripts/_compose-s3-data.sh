#!/usr/bin/env bash

rm -rf generated/
mkdir -p generated/backend
mkdir -p generated/frontend
mkdir -p generated/systemd
cp ../condorcet-backend/console/target/condorcet-backend-console.jar generated/backend/
cp ../mysql-connect-test/target/mysql-connect-test.jar generated/backend/
cp ../json-console/app/target/json-console-app.jar generated/backend/edit-json.jar
cp -r ../condorcet-frontend/build/ generated/frontend
cp resources/condorcet-backend.service generated/systemd
pushd generated/backend
zip -r ../backend.zip .
popd
pushd generated/frontend
zip -r ../frontend.zip .
popd
pushd generated/systemd
zip -r ../systemd.zip .
popd

mkdir -p generated/s3/ec2
cp generated/backend.zip generated/s3/ec2
cp generated/systemd.zip generated/s3/ec2

mkdir -p generated/s3/website
cp -r generated/frontend/ generated/s3/website
