#!/bin/bash

# mvn -DLogback.configurationFile=./src/main/resources/logback-prod.xml spring-boot:run
./crawl.sh > /dev/null 2>&1 &
