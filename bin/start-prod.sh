#!/bin/bash

bin=$(dirname "$0")
bin=$(cd "$bin">/dev/null || exit; pwd)
APP_HOME=$(cd "$bin"/..>/dev/null || exit; pwd)

APP_DATA="$HOME/.pulsar"
export APP_LOG_HOME="$APP_DATA/logs"

LOGBACK_CONFIG_FILE_LOCATION="$APP_HOME"/conf/logback-prod.xml
if [[ -e "$LOGBACK_CONFIG_FILE_LOCATION" ]]; then
  export LOGBACK_CONFIG_FILE_LOCATION="$LOGBACK_CONFIG_FILE_LOCATION"
fi

PROXY_PROVIDER_FILE_LOCATION=$HOME/proxy.providers.txt
if [[ -e "$PROXY_PROVIDER_FILE_LOCATION" ]]; then
  "$bin"/tools/proxy/proxymgr epd -all
fi

export APP_TMP_DIR=$HOME/tmp
if [[ ! -e "$APP_TMP_DIR" ]]; then
  mkdir "$APP_TMP_DIR"
fi

ENV=prod "$APP_HOME"/bin/start.sh -D -HL -pc 8 -mt 10
