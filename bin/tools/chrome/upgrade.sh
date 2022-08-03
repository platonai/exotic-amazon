#!/usr/bin/env bash

install_utils() {
  if ! command -v unzip &> /dev/null
  then
      sudo apt-get install unzip
  fi
}

install_chrome() {
    echo "Installing latest stable google-chrome"

    install_utils

    wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
    sudo dpkg -i google-chrome*.deb
    sudo apt-get install -f

    google-chrome -version
}

cd /tmp/ || exit

# find out chrome version
CHROME_VERSION="$(google-chrome -version | head -n1 | awk -F '[. ]' '{print $3}')"

echo "Current chrome: $CHROME_VERSION"
echo "Do you want to upgrade chrome to the latest version?Y/n"
read -r line

if [[ "$line" == "Y" ]]; then
    install_chrome
fi

cd - || exit
