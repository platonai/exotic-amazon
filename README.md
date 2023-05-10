# Exotic Amazon 自述文件

[English](README.EN.md) | 简体中文

Exotic Amazon 是采集整个 amazon.com 网站的完整解决方案，**开箱即用**，包含亚马逊大多数数据类型，它将永久免费提供并开放源代码。

其他电商平台数据采集，其方法和流程基本类似，可以在该项目基础上修改调整业务逻辑即可，其基础设施解决了所有大规模数据采集面临的难题。

得益于 [PulsarR](https://github.com/platonai/pulsarr/README.adoc) ([中文](https://github.com/platonai/pulsarr/blob/master/README-CN.adoc)) 提供的完善的 **Web 数据管理基础设施**，整个解决方案由不超过 3500 行的 kotlin 代码和不到 700 行的 X-SQL 组成，以提取 650 多个字段。

### 数据简介

* Best Seller - 每天更新，约 32,000 个类别，约 4,000,000 个产品记录
* Most Wished For - 每天更新约 25,000 个类别，约 3,500,000 个产品记录
* New Releases - 每天更新，约 25,000 个类别，约 3,000,000 条产品记录
* Movers and Shakers - 约 20 个类别，每小时更新一次
* Products - 约 20,000,000 个产品，每月更新
  * 100多个字段
  * 标题、价格、库存、图像、描述、规格、店铺等
  * 赞助产品、类似产品、相关产品等
  * 阅读评论
  * 热门评论
* Review - 每天更新

## 开始

如果 maven 版本号是 3.8.1 或以上，需要在 `.m2/settings.xml` 文件中加入如下代码：

    <mirrors>
        <mirror>
            <id>maven-default-http-blocker</id>
            <mirrorOf>dummy</mirrorOf>
            <name>Dummy mirror to override default blocking mirror that blocks http</name>
            <url>http://0.0.0.0/</url>
        </mirror>
    </mirrors>

或者如果这个文件不存在，可以直接拷贝 [settings.xml](docs/settings.xml) 到 .m2 目录下。在 Q/A 章节可以找到关于 .m2/settings.xml 的基本介绍。

现在可以开始了构建了：

    # build
    git clone https://github.com/platonai/exotic-amazon.git
    cd exotic-amazon && mvn -DskipTests=true

    ####################
    # On Linux:

    # run in development mode
    java -jar target/exotic-amazon*.jar
    # run in production mode
    ENV=prod ./crawl.sh
    # run in production mode
    ENV=prod java -jar target/exotic-amazon*.jar

    ####################
    # On Windows:

    # run in development mode
    java -jar target/exotic-amazon-{the-actual-version}.jar
    # run in production mode, (there is a bug, fix coming soon)
    ENV=prod java -jar target/exotic-amazon-{the-actual-version}.jar

一旦运行成功，你可以打开 [System Glances](http://localhost:8182/api/system/status/glances) 以一目了然地查看系统状态。

## 困难和挑战

现在主流网站常用的反爬手段基本都用了，譬如Cookie跟踪，IP跟踪，访问频率限制，访问轨迹跟踪，CSS 混淆等等。

使用基本的 HTTP 协议采集的话，会陷入无穷无尽的爬虫/反爬虫对抗中，得不偿失，并且未必能解决，譬如说采用了动态自定义字体的站点就不可能解决。

使用浏览器自动化工具如 selenium, playwright, puppeteer 等进行数据采集，会被检测出来并直接屏蔽。

使用 puppeteer-extra, apify/crawlee 这样的工具，虽然提供了 WebDriver 隐身特性，一定程度上缓解了这个问题，但仍然没有完全解决。

1. 上述工具没有解决访问轨迹跟踪问题
2. Headless 模式能够被检测出来。云端爬虫通常以 headless 模式运行，即使做了 WebDriver 隐身, headless 模式也能够被检测出来
3. 其他爬虫对抗问题

即使解决完上述问题，也仅仅是入门而已。在大规模采集下，仍然面临诸多困难：

1. 如何正确轮换IP？事实上，仅轮换IP是不够的，我们提出“**隐私上下文轮换**”
2. 如何使用单台机器每天提取**数千万数据点**？
3. 如何保证**数据准确性**？
4. 如何保证**调度准确性**？
5. 如何保证**分布式系统弹性**？
6. 如何正确提取 **CSS 混淆** 的字段，它的 CSSPath/XPath/Regex 每个网页都不同，有什么技术解决？
7. 如何采集数百个电商站点并避免爬虫失效？
8. 如何降低**总体拥有成本**？

## 提取结果处理

### 提取规则

所有 [提取规则](./src/main/resources/sites/amazon/crawl/parse/sql/crawl/) 都是用 X-SQL 编写的。数据类型转换、数据清理也由强大的 X-SQL 内联处理，这也是我们需要 X-SQL 的部分原因。

一个很好的 X-SQL 例子是 x-asin.sql，它从每个产品页面中提取 70 多个字段: [x-asin.sql](./src/main/resources/sites/amazon/crawl/parse/sql/crawl/x-asin.sql).

### 将提取结果保存在本地文件系统中

默认情况下，结果以 json 格式写入本地文件系统:

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

### 将提取结果保存到数据库中

有几种方法可以将结果保存到数据库中:

1. 将结果序列化为键值对，并保存为 WebPage 对象的一个字段，这是整个系统的核心数据结构
2. 将结果写入 JDBC 兼容的数据库，如 MySQL、PostgreSQL、MS SQL Server、Oracle 等
3. 自行编写几行代码，将结果保存到您希望的任何目的地

#### 保存到 WebPage.pageModel

默认情况下，提取的字段也作为键值对保存到 [WebPage.pageModel](https://github.com/platonai/pulsarr/blob/master/pulsar-persist/src/main/java/ai/platon/pulsar/persist/WebPage.java).

#### 保存到JDBC兼容的数据库

* 正确配置 AmazonJdbcSinkSQLExtractor.jdbcCommitter
* 数据库模式: [schema](./src/main/resources/schema)
* 页面模型和数据库模式映射: [extract-config.json](./src/main/resources/sites/amazon/crawl/parse/extract-config.json)
* 页面模型和提取规则: [X-SQLs](./src/main/resources/sites/amazon/crawl/parse/sql/crawl/)

#### 保存到自定义目的地

您可以编写几行附加代码，将结果保存到您希望的任何目的地, 查看 [AmazonJdbcSinkSQLExtractor](./src/main/kotlin/ai/platon/exotic/amazon/crawl/boot/component/AmazonJdbcSinkSQLExtractor.kt).onAfterExtract() 了解如何编写自己的持久层。

## 技术特性

* RPA：机器人流程自动化、模仿人类行为、采集单网页应用程序或执行其他有价值的任务
* 高性能：高度优化，单机并行渲染数百页而不被屏蔽
* 低成本：每天抓取 100,000 个浏览器渲染的电子商务网页，或 n * 10,000,000 个数据点，仅需要 8 核 CPU/32G 内存
* 数据质量保证：智能重试、精准调度、Web 数据生命周期管理
* 简洁的 API：一行代码抓取，或者一条 SQL 将整个网站栏目变成表格
* X-SQL：扩展 SQL 来管理 Web 数据：网络爬取、数据采集、Web 内容挖掘、Web BI
* 爬虫隐身：浏览器驱动隐身，IP 轮换，隐私上下文轮换，永远不会被屏蔽
* 大规模采集：完全分布式，专为大规模数据采集而设计
* 大数据支持：支持各种后端存储：本地文件/MongoDB/HBase/Gora
* 日志和指标：密切监控并记录每个事件

## 系统要求

* Minimum memory requirement is 4G, 8G is recommended for test environment, 32G is recommended for product environment
* The latest version of the Java 11 JDK
* Java and jar on the PATH
* Maven 3.2+
* Google Chrome 90+
* MongoDB started

## 日志和指标

PulsarR 精心设计了日志和指标子系统，以记录系统中发生的每一个事件。

PulsarR 在日志中报告每个页面加载任务执行的状态，因此很容易知道系统中发生了什么，判断系统运行是否健康、回答成功获取多少页面、重试多少页面、使用了多少代理 IP。

只需注意几个符号，您就可以深入了解整个系统的状态：💯 💔 🗙 ⚡ 💿 🔃 🤺。

下面是一组典型的页面加载日志，查看 [日志格式](https://github.com/platonai/pulsarr/blob/master/docs/log-format.adoc) 了解如何阅读日志，从而一目了然地了解整个系统的状态。

```
2022-09-24 11:46:26.045  INFO [-worker-14] a.p.p.c.c.L.Task - 3313. 💯 ⚡ U for N got 200 580.92 KiB in 1m14.277s, fc:1 | 75/284/96/277/6554 | 106.32.12.75 | 3xBpaR2 | https://www.walmart.com/ip/Restored-iPhone-7-32GB-Black-T-Mobile-Refurbished/329207863 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:46:09.190  INFO [-worker-32] a.p.p.c.c.L.Task - 3738. 💯 💿 U  got 200 452.91 KiB in 55.286s, last fetched 9h32m50s ago, fc:1 | 49/171/82/238/6172 | 121.205.220.179 | https://www.walmart.com/ip/Boost-Mobile-Apple-iPhone-SE-2-Cell-Phone-Black-64GB-Prepaid-Smartphone/490934488 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:46:28.567  INFO [-worker-17] a.p.p.c.c.L.Task - 2269. 💯 🔃 U for SC got 200 565.07 KiB <- 543.41 KiB in 1m22.767s, last fetched 16m58s ago, fc:6 | 58/230/98/295/6272 | 27.158.125.76 | 9uwu602 | https://www.walmart.com/ip/Straight-Talk-Apple-iPhone-11-64GB-Purple-Prepaid-Smartphone/356345388?variantFieldId=actual_color -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:47:18.390  INFO [r-worker-8] a.p.p.c.c.L.Task - 3732. 💔 ⚡ U for N got 1601 0 <- 0 in 32.201s, fc:1/1 Retry(1601) rsp: CRAWL, rrs: EMPTY_0B | 2zYxg52 | https://www.walmart.com/ip/Apple-iPhone-7-256GB-Jet-Black-AT-T-Locked-Smartphone-Grade-B-Used/182353175?variantFieldId=actual_color -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:47:13.860  INFO [-worker-60] a.p.p.c.c.L.Task - 2828. 🗙 🗙 U for SC got 200 0 <- 348.31 KiB <- 684.75 KiB in 0s, last fetched 18m55s ago, fc:2 | 34/130/52/181/5747 | 60.184.124.232 | 11zTa0r2 | https://www.walmart.com/ip/Walmart-Family-Mobile-Apple-iPhone-11-64GB-Black-Prepaid-Smartphone/209201965?athbdg=L1200 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
```

有三种方法可以查看指标：

* Check logs/pulsar.m.log
* Open [System Glances](http://localhost:8182/api/system/status/glances) which is a Web UI to show the most metrics
* Install [graphite](https://graphiteapp.org/) on the same machine, and open http://127.0.0.1/ to view the graphical report

## Q & A

### **Q: 如何使用代理IP？**

A: [点击查看](https://github.com/platonai/exotic/blob/main/bin/tools/proxy/README.adoc) 如何管理 IP

### **Q: .m2/settings.xml 是什么文件？**

A: 它是用来设置 maven 参数的配置文件。`Settings.xml` 中包含本地仓库位置、修改远程仓库服务器、认证信息等配置。一般存在于两个位置：

全局配置：

    ${maven.home}/conf/settings.xml

用户配置：

    ${user.home}/.m2/settings.xml

如果这个文件不存在，你可以拷贝 [`settings.xml`](docs/settings.xml) 到 `.m2` 目录下。

### **Q: 先抓取详情页，再根据详情页抓取评论页，这一块处理的逻辑在哪里？**

A: 你可以看看下面几个调用的 [代码](src/main/kotlin/ai/platon/exotic/amazon/crawl/boot/component/AmazonJdbcSinkSQLExtractor.kt) 逻辑：

````
AmazonJdbcSinkSQLExtractor.collectHyperlinks ->
 amazonLinkCollector.collectReviewLinksFromProductPage,
 amazonLinkCollector.collectSecondaryReviewLinks,
 amazonLinkCollector.collectSecondaryReviewLinksFromPagination
````

### **Q: 怎样设置任务的启动时间、结束时间和采集周期？**

A: 

1. 阅读 [LoadOptions](https://github.com/platonai/pulsarr/blob/master/docs/concepts-CN.adoc#_load_options) 文档，它描述一个任务该怎么处理
2. 参考 [PredefinedTask](src/main/kotlin/ai/platon/exotic/amazon/crawl/core/PredefinedTasks.kt)，它定义了亚马逊特定任务。PredefinedTask 的设置最终会被转换 LoadOptions 参数
3. 定时任务在 [CrawlScheduler](src/main/kotlin/ai/platon/exotic/amazon/crawl/boot/CrawlScheduler.kt) 中设置

### **Q: 怎么保存抓取结果？**

A: 参看本文档 [将提取结果保存到数据库中](#将提取结果保存到数据库中) 章节。

## 联系作者
微信：galaxyeye
微博：galaxyeye
邮箱：galaxyeye@live.cn, ivincent.zhang@gmail.com
Twitter: galaxyeye8
