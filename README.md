# Exotic Amazon README

Exotic Amazon is a complete solution to crawl the entire website of amazon.com.

## Data

* Best Seller - update every day, about 32,000 categories, about 4,000,000 critical data of products
* Most Wished For - update every day, about 25,000 categories, about 3,500,000 critical data of products
* New Releases - update every day, about 25,000 categories, about  3,000,000 critical data of products
* Movers and Shakers - about 20 categories, update every hour
* Products - about 20,000,000 products, update every month
  * 100+ fields
  * Title, price, stock, image, description, specification, shop
  * Sponsored products, similar products, related products
  * Read reviews
  * Top reviews
* Review - update every day

## Get Started

    git clone https://github.com/platonai/exotic-amazon.git
    cd exotic-amazon && mvn
    java -jar target/exotic-amazon*.jar

Open [System Glances](http://localhost:8182/api/system/status/glances) to see the system status at a glance.

## Results

The results are written in json:

    cd /tmp/pulsar-$USER/cache/web/export/amazon/json
    ls

## Technical Features

* Web spider: browser rendering, ajax data crawling

* High performance: highly optimized, rendering hundreds of pages in parallel on a single machine without be blocked

* Low cost: scraping 100,000 browser rendered e-comm webpages, or n * 10,000,000 data points each day, only 8 core CPU/32G memory are required

* Data quantity assurance: smart retry, accurate scheduling, web data lifecycle management

* Large scale: fully distributed, designed for large scale crawling

* Simple API: single line of code to scrape, or single SQL to turn a website into a table

* X-SQL: extended SQL to manage web data: Web crawling, scraping, Web content mining, Web BI

* Bot stealth: web driver stealth, IP rotation, privacy context rotation, never get banned

* RPA: simulating human behaviors, SPA crawling, or do something else awesome

* Big data: various backend storage support: MongoDB/HBase/Gora

* Logs & metrics: monitored closely and every event is recorded

## Requirements

* Memory 4G+
* The latest version of the Java 11 JDK
* Java and jar on the PATH
* Google Chrome 90+
* MongoDB started
