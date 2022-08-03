@echo off

set chrome="C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
set userDataDir="C:\Users\Vincent Zhang\.pulsar\browser\chrome\prototype\google-chrome"

%chrome% --user-data-dir=%userDataDir%

@echo on
