#!/bin/bash

bin=$(dirname "$0")
bin=$(cd "$bin">/dev/null || exit; pwd)

ENV=prod "$bin"/start.sh -D -HL -pc 4 -mt 15
