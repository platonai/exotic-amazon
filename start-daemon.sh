#!/bin/bash

bin=$(dirname "$0")
bin=$(cd "$bin">/dev/null || exit; pwd)

"$bin"/start.sh -D -HL -pc 2 -mt 15
