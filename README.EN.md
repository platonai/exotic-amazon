# Exotic Amazon README

English | [简体中文](README.md)

Exotic Amazon is a complete solution to crawl the entire site of amazon.com.

Thanks to the perfect Web data management infrastructure provided by Pulsar, the entire solution consists of no more than 3,500 lines of kotlin code, plus less than 700 lines of X-SQL to extract more than 650 fields.

## Data

* Best Seller - update every day, about 32,000 categories, about 4,000,000 product records
* Most Wished For - update every day, about 25,000 categories, about 3,500,000 product records
* New Releases - update every day, about 25,000 categories, about 3,000,000 product records
* Movers and Shakers - about 20 categories, update every hour
* Products - about 20,000,000 products, update every month
  * 100+ fields
  * Title, price, stock, image, description, specification, shop, and more
  * Sponsored products, similar products, related products, and more
  * Read reviews
  * Top reviews
* Review - update every day

## Get Started

If the maven version number is 3.8.1 or above, you need to add the following code to `.m2/settings.xml`:

    <mirrors>
        <mirror>
            <id>maven-default-http-blocker</id>
            <mirrorOf>dummy</mirrorOf>
            <name>Dummy mirror to override default blocking mirror that blocks http</name>
            <url>http://0.0.0.0/</url>
        </mirror>
    </mirrors>

If this file doesn't exist, you can copy [settings.xml](docs/settings.xml) to `.m2` directory. A basic introduction to `.m2/settings.xml` can be found in the Q/A section.

Now we can start building:

    git clone https://github.com/platonai/exotic-amazon.git
    cd exotic-amazon && mvn -DskipTests=true
    java -jar target/exotic-amazon*.jar
    
Or on Windows:
    
    java -jar target/exotic-amazon-{the-actual-version}.jar
    
Open [System Glances](http://localhost:8182/api/system/status/glances) to see the system status at a glance.

## Results

### Extract rules

All [extract rules](./src/main/resources/sites/amazon/crawl/parse/sql/crawl/) are written in X-SQL. Data type conversion, data cleansing are also handled inline by powerful X-SQL, which is part of the reason why we need X-SQL.

A good X-SQL example is x-asin.sql which extracts 70+ fields from each product page: [x-asin.sql](./src/main/resources/sites/amazon/crawl/parse/sql/crawl/x-asin.sql).

### Save extract results in the local file system

The results are written in json to local file system by default:

Linux:

    cd /tmp/pulsar-$USER/cache/web/export/amazon/json
    ls

Windows:

    echo %TMP%
    echo %username%
    cd %TMP%\pulsar-%username%/cache/web/export/amazon/json
    dir

Mac:

    echo $TMPDIR
    echo $USER
    echo $TMPDIR/pulsar-$USER/cache/web/export/amazon/json
    ls

### Save extract results into a database

There are several methods to persist the results into a database:

1. Serialize the results as key-value pairs, and save them as a field of WebPage, which is the core data structure across the whole system
2. Write the results to a JDBC compatible database, such as MySQL, PostgreSQL, MS SQL Server, Oracle, etc
3. Save the results to any destination as you wish by writing several line of additional code yourself

#### Save as WebPage.pageModel

By default, the extracted fields are also saved as key-value pairs to 
[WebPage.pageModel](https://github.com/platonai/pulsarr/blob/master/pulsar-persist/src/main/java/ai/platon/pulsar/persist/WebPage.java).

#### Save to a JDBC compatible database

* Database connection config: [jdbc-sink-config.json](./src/main/resources/config/jdbc-sink-config.json)
* Database schema: [schema](./src/main/resources/schema)
* Page model and database schema mapping: [extract-config.json](./src/main/resources/sites/amazon/crawl/parse/extract-config.json)
* Page model and extract rules: [X-SQLs](./src/main/resources/sites/amazon/crawl/parse/sql/crawl/)

#### Save to a custom destination

You can write several line of additional code to save the results to any destination as you wish, check [AmazonJdbcSinkSQLExtractor](./src/main/kotlin/ai/platon/exotic/amazon/crawl/boot/component/AmazonJdbcSinkSQLExtractor.kt).onAfterExtract() to learn how to write your own persistence layer.

## Technical Features

* X-SQL: extended SQL to manage web data: Web crawling, scraping, Web content mining, Web BI
* Bot stealth: web driver stealth, IP rotation, privacy context rotation, never get banned
* High performance: highly optimized, rendering hundreds of pages in parallel on a single machine without be blocked
* Low cost: scraping 100,000 browser rendered e-comm webpages, or n * 10,000,000 data points each day, only 8 core CPU/32G memory are required
* Data quantity assurance: smart retry, **accurate scheduling**, web data lifecycle management
* Large scale: fully distributed, designed for large scale crawling
* Big data: various backend storage support: Local File/MongoDB/HBase/Gora
* Logs &amp; metrics: monitored closely and every event is recorded

## Requirements

* Minimum memory requirement is 4G, 8G is recommended for test environment, 32G is recommended for product environment
* The latest version of the Java 11 JDK
* Java and jar on the PATH
* Google Chrome 90+
* MongoDB started

## Logs & Metrics

Pulsar has carefully designed the logging and metrics subsystem to record every event that occurs in the system.

Pulsar logs the status for every load execution, so it's easy to know what happened in the system, find out answers such as is the system running healthy, how many pages were successfully fetched, how many pages were retried, how many proxy ips were used, etc.

Typical page loading logs are as the following, check [log-format](https://github.com/platonai/pulsarr/blob/master/docs/log-format.adoc) to learn how to read the logs to learn the state of the whole system at a glance.

```
2022-09-24 11:46:26.045  INFO [-worker-14] a.p.p.c.c.L.Task - 3313. 💯 ⚡ U for N got 200 580.92 KiB in 1m14.277s, fc:1 | 75/284/96/277/6554 | 106.32.12.75 | 3xBpaR2 | https://www.walmart.com/ip/Restored-iPhone-7-32GB-Black-T-Mobile-Refurbished/329207863 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:46:09.190  INFO [-worker-32] a.p.p.c.c.L.Task - 3738. 💯 💿 U  got 200 452.91 KiB in 55.286s, last fetched 9h32m50s ago, fc:1 | 49/171/82/238/6172 | 121.205.220.179 | https://www.walmart.com/ip/Boost-Mobile-Apple-iPhone-SE-2-Cell-Phone-Black-64GB-Prepaid-Smartphone/490934488 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:46:28.567  INFO [-worker-17] a.p.p.c.c.L.Task - 2269. 💯 🔃 U for SC got 200 565.07 KiB <- 543.41 KiB in 1m22.767s, last fetched 16m58s ago, fc:6 | 58/230/98/295/6272 | 27.158.125.76 | 9uwu602 | https://www.walmart.com/ip/Straight-Talk-Apple-iPhone-11-64GB-Purple-Prepaid-Smartphone/356345388?variantFieldId=actual_color -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:47:18.390  INFO [r-worker-8] a.p.p.c.c.L.Task - 3732. 💔 ⚡ U for N got 1601 0 <- 0 in 32.201s, fc:1/1 Retry(1601) rsp: CRAWL, rrs: EMPTY_0B | 2zYxg52 | https://www.walmart.com/ip/Apple-iPhone-7-256GB-Jet-Black-AT-T-Locked-Smartphone-Grade-B-Used/182353175?variantFieldId=actual_color -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:47:13.860  INFO [-worker-60] a.p.p.c.c.L.Task - 2828. 🗙 🗙 U for SC got 200 0 <- 348.31 KiB <- 684.75 KiB in 0s, last fetched 18m55s ago, fc:2 | 34/130/52/181/5747 | 60.184.124.232 | 11zTa0r2 | https://www.walmart.com/ip/Walmart-Family-Mobile-Apple-iPhone-11-64GB-Black-Prepaid-Smartphone/209201965?athbdg=L1200 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
```

There are three ways to view metrics:

* Check logs/pulsar.m.log
* Open [System Glances](http://localhost:8182/api/system/status/glances) which is a Web UI to show the most metrics
* Install [graphite](https://graphiteapp.org/) on the same machine, and open http://127.0.0.1/ to view the graphical report

## Q & A
### **Q: How to use proxies?**

A: Follow [this](https://github.com/platonai/exotic/blob/main/bin/tools/proxy/README.adoc) guide for proxy rotation.

### **Q: What is `.m2/settings.xml`?**

A: It is a configuration file for maven. Settings.xml contains configuration like local repository location, remote repository server, authentication information, etc. It generally exists in two locations:

Global settings:

    ${maven.home}/conf/settings.xml

User settings:

    ${user.home}/.m2/settings.xml

If this file doesn't exist, you can copy [settings.xml](docs/settings.xml) to the `.m2` directory.

### **Q: First scrape the detail page and then scrape the comment pages from the detail page, where is the code? **

A: You can see the [code logic](src/main/kotlin/ai/platon/exotic/amazon/crawl/boot/component/AmazonJdbcSinkSQLExtractor.kt) for the following calls:

````
AmazonJdbcSinkSQLExtractor.collectHyperlinks ->
 amazonLinkCollector.collectReviewLinksFromProductPage,
 amazonLinkCollector.collectSecondaryReviewLinks,
 amazonLinkCollector.collectSecondaryReviewLinksFromPagination
````

### **Q: How to set the start time, end time and scrape period of tasks?**

A:

1. Read [LoadOptions](https://github.com/platonai/pulsarr/blob/master/docs/concepts-CN.adoc#_load_options) which describe tasks
2. Refer to [PredefinedTask](src/main/kotlin/ai/platon/exotic/amazon/crawl/core/PredefinedTasks.kt), which defines Amazon specific tasks. The settings of PredefinedTask will eventually be converted to LoadOptions
3. Scheduled tasks are set in [CrawlScheduler](src/main/kotlin/ai/platon/exotic/amazon/crawl/boot/CrawlScheduler.kt)

### **Q: How to store the scraping results?**

A: Refer to [Save extract results into a database](#Save extract results into a database)
