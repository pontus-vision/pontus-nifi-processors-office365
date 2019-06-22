#!/bin/bash

DIR="$( cd "$(dirname "$0")" ; pwd -P )"
cd $DIR/docker
docker build --rm . -t pontusvisiongdpr/pontus-nifi-processors-office365-lib

docker push pontusvisiongdpr/pontus-nifi-processors-office365-lib

