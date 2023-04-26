#!/bin/bash

browser.display.mode=HEADLESS
# mvn -DLogback.configurationFile=./src/main/resources/logback-prod.xml spring-boot:run
mvn spring-boot:run
