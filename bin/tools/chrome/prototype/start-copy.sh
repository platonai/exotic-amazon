#!/usr/bin/env bash

prototypeDataDir=~/.pulsar/browser/chrome/prototype/google-chrome
now=$(date +%s)

userDataDir="/tmp/pulsar-$USER/context/browser$now"

if [ ! -e "$userDataDir" ]; then
  echo "Copy data from $prototypeDataDir to $userDataDir"
  cp -r "$prototypeDataDir" "$userDataDir"
  rm "$userDataDir/SingletonCookie"
  rm "$userDataDir/SingletonLock"
  unlink "$userDataDir/SingletonSocket"
fi

userDataDir=$(cd "$userDataDir">/dev/null || exit; pwd)

/usr/bin/google-chrome-stable --user-data-dir="$userDataDir" "https://www.tmall.com/"
