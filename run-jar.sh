#!/bin/bash

ENV=prod java -Xmx10g -Xms2G \
  -Dapp.tmp.dir="$HOME/tmp" \
  -jar target/exotic-amazon-*.jar \
  --headless --privacy 5 --maxTabs 12 \
 > /dev/null 2>&1 &
