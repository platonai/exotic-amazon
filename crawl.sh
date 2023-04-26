#!/bin/bash

# mvn -DLogback.configurationFile=./src/main/resources/logback-prod.xml spring-boot:run
mvn spring-boot:run -Dbrowser.display.mode=HEADLESS
