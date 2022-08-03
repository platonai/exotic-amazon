#!/usr/bin/env bash

# start our prototype chrome

userDataDir=~/.pulsar/browser/chrome/prototype/google-chrome
# absolute path is required
userDataDir=$(cd "$userDataDir">/dev/null || exit; pwd)

/usr/bin/google-chrome-stable --user-data-dir="$userDataDir" "https://www.amazon.com/dp/B00FMWWN6U"
