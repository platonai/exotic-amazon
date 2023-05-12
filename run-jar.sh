#!/bin/bash

ENV=prod java -Xmx10g -Xms2G \
  -jar target/exotic-amazon-*.jar \
  --headless --privacy 5 --maxTabs 12 \
  -Dapp.tmp.dir="$HOME/tmp" \
 > /dev/null 2>&1 &
