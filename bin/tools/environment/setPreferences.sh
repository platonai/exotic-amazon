#!/bin/bash

if [ $# == 0 ]; then
  echo setPreferences.sh tld
  exit 1
fi

TLD=$1
shift

LOG_DIR=logs/env/"$TLD"
if [[ -e $LOG_DIR ]]; then
  COUNT=$(ls -l "$LOG_DIR/.." | grep -c "$TLD")
  mv "$LOG_DIR" "$LOG_DIR.$COUNT"
fi

domain="amazon.$TLD"

java -Xmx10g -Xms2G -Dbrowser.display.mode=HEADLESS -cp target/exotic-amazon*.jar \
-D"loader.main=ai.platon.exotic.amazon.tools.environment.ChooseDistrictKt" \
-D"logging.dir=$LOG_DIR" \
org.springframework.boot.loader.PropertiesLauncher "$domain"
