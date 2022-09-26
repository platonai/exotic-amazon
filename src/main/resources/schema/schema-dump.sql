-- MySQL dump 10.13  Distrib 8.0.22, for Linux (x86_64)
--
-- Host: 42.194.237.180    Database: mallbigdata_us
-- ------------------------------------------------------
-- Server version	5.7.26-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `asin_ad_sync`
--

DROP TABLE IF EXISTS `asin_ad_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_ad_sync` (
  `asin` varchar(255) DEFAULT NULL COMMENT '商品asin编码',
  `carousel_title` varchar(255) DEFAULT NULL COMMENT 'The title of this ad section',
  `is_sponsored` varchar(255) DEFAULT NULL COMMENT 'The sponsored tag',
  `ad_asin` varchar(255) DEFAULT NULL COMMENT '广告商品asin编码',
  `ad_asin_bsr` varchar(255) DEFAULT NULL,
  `ad_asin_url` varchar(2048) DEFAULT NULL COMMENT '广告商品URL',
  `ad_asin_title` varchar(255) DEFAULT NULL COMMENT '广告商品名称',
  `ad_asin_price` varchar(255) DEFAULT NULL COMMENT '广告商品价格',
  `ad_asin_img` varchar(2048) DEFAULT NULL COMMENT '广告商品图片',
  `ad_asin_score` varchar(255) DEFAULT NULL,
  `ad_asin_score_2` varchar(255) DEFAULT NULL COMMENT 'The additional field for score, take this one if ad_asin_score is empty',
  `ad_asin_starnum` varchar(255) DEFAULT NULL,
  `ad_asin_soldby` text COMMENT '广告商品卖家',
  `ad_asin_soldby_url` varchar(2048) DEFAULT NULL COMMENT '广告商品卖家url',
  `ad_asin_shipby` text COMMENT '广告商品物流',
  `ad_asin_position` int(11) DEFAULT NULL COMMENT 'The position in the carousel.',
  `ad_type` varchar(255) DEFAULT NULL COMMENT '广告类型: Sponsored/similar/also viewed／',
  `url` varchar(2048) DEFAULT NULL COMMENT '商品asin编码',
  `label` varchar(32) DEFAULT NULL COMMENT 'multiple purpose label, e.g., a task id',
  `task_time` datetime DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=37329137 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asin_best_sellers_sync`
--

DROP TABLE IF EXISTS `asin_best_sellers_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_best_sellers_sync` (
  `url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `asin` varchar(512) DEFAULT NULL COMMENT 'asin',
  `asin_url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `selectedcategory` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `categorylevel` varchar(512) DEFAULT NULL COMMENT '分类层级',
  `category_url` varchar(2048) DEFAULT NULL COMMENT '所属分类 url',
  `categoryinurl` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `rank` int(11) DEFAULT NULL COMMENT 'price',
  `price` varchar(512) DEFAULT NULL COMMENT 'price',
  `title` varchar(512) DEFAULT NULL COMMENT '名称',
  `pic` varchar(2048) DEFAULT NULL COMMENT '图片URL',
  `score` varchar(512) DEFAULT NULL COMMENT '平均评星数',
  `starnum` varchar(512) DEFAULT NULL COMMENT '评星总数',
  `task_time` datetime DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=46060302 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asin_most_wished_for_sync`
--

DROP TABLE IF EXISTS `asin_most_wished_for_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_most_wished_for_sync` (
  `url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `asin` varchar(512) DEFAULT NULL COMMENT 'asin',
  `asin_url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `selectedcategory` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `categorylevel` varchar(512) DEFAULT NULL COMMENT '分类层级',
  `category_url` varchar(2047) DEFAULT NULL COMMENT '所属分类 url',
  `categoryinurl` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `rank` varchar(512) DEFAULT NULL COMMENT '自然排名',
  `price` varchar(512) DEFAULT NULL COMMENT 'price',
  `offers` varchar(512) DEFAULT NULL COMMENT 'price',
  `title` varchar(512) DEFAULT NULL COMMENT '名称',
  `pic` varchar(2048) DEFAULT NULL COMMENT '图片URL',
  `score` varchar(512) DEFAULT NULL COMMENT '平均评星数',
  `starnum` varchar(512) DEFAULT NULL COMMENT '评星总数',
  `task_time` datetime DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `sales_rank_change` varchar(512) DEFAULT NULL,
  `sales_rank` varchar(512) DEFAULT NULL,
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=40278243 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asin_movers_and_shakers_sync`
--

DROP TABLE IF EXISTS `asin_movers_and_shakers_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_movers_and_shakers_sync` (
  `url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `asin` varchar(512) DEFAULT NULL COMMENT 'asin',
  `asin_url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `selectedcategory` varchar(512) DEFAULT NULL COMMENT '所属分类 url',
  `categorylevel` varchar(512) DEFAULT NULL COMMENT '所属分类 url',
  `categoryinurl` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `category_url` varchar(2048) DEFAULT NULL COMMENT '所属分类 url',
  `rank` varchar(512) DEFAULT NULL COMMENT '自然排名',
  `price` varchar(512) DEFAULT NULL COMMENT 'price',
  `title` varchar(255) DEFAULT NULL COMMENT '名称',
  `pic` varchar(2048) DEFAULT NULL COMMENT '图片URL',
  `score` varchar(255) DEFAULT NULL COMMENT '平均评星数',
  `starnum` varchar(255) DEFAULT NULL COMMENT '评星总数',
  `sales_rank` varchar(255) DEFAULT NULL COMMENT 'bsr排名',
  `sales_rank_change` varchar(255) DEFAULT NULL COMMENT '现在bsr排名',
  `task_time` datetime DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `offers` varchar(512) DEFAULT NULL,
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=2742067 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asin_new_releases_sync`
--

DROP TABLE IF EXISTS `asin_new_releases_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_new_releases_sync` (
  `url` varchar(2048) DEFAULT NULL COMMENT '页面 url',
  `asin` varchar(512) DEFAULT NULL COMMENT 'asin',
  `asin_url` varchar(2048) DEFAULT NULL COMMENT 'asin',
  `selectedcategory` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `categorylevel` varchar(512) DEFAULT NULL COMMENT '分类层级',
  `category_url` varchar(2048) DEFAULT NULL COMMENT '所属分类 url',
  `categoryinurl` varchar(512) DEFAULT NULL COMMENT '所属分类nodeID',
  `rank` varchar(512) DEFAULT NULL COMMENT '排名',
  `offers` varchar(512) DEFAULT NULL COMMENT 'offers from',
  `price` varchar(512) DEFAULT NULL COMMENT 'price',
  `title` varchar(512) DEFAULT NULL COMMENT '名称',
  `pic` varchar(2048) DEFAULT NULL COMMENT '图片URL',
  `score` varchar(512) DEFAULT NULL COMMENT '平均评星数',
  `starnum` varchar(512) DEFAULT NULL COMMENT '评星总数',
  `task_time` datetime DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=20075453 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asin_review_sync`
--

DROP TABLE IF EXISTS `asin_review_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_review_sync` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `url` varchar(2047) DEFAULT NULL COMMENT '网页链接',
  `href` varchar(2048) DEFAULT NULL COMMENT 'the hypertext reference from which we can click on and lead to this page',
  `label` varchar(32) DEFAULT NULL COMMENT 'multiple purpose label.',
  `asin` varchar(1023) DEFAULT NULL COMMENT '商品父pasin编码',
  `ratingcount` varchar(2047) DEFAULT NULL COMMENT '评论链接',
  `reviews_url` varchar(2047) DEFAULT NULL COMMENT '评论链接',
  `sku_asin` varchar(255) DEFAULT NULL COMMENT '变体sku组合对应的asin',
  `comment_id` varchar(255) DEFAULT NULL COMMENT '评论ID',
  `comment_time` varchar(255) DEFAULT NULL COMMENT '评论时间',
  `comment_name` varchar(255) DEFAULT NULL COMMENT '评论人',
  `comment_title` varchar(511) DEFAULT NULL COMMENT '评论标题',
  `comment_name_url` varchar(1023) DEFAULT NULL COMMENT '评论人URL',
  `is_verified` varchar(32) DEFAULT NULL COMMENT '验证购买: Verified Purchase',
  `content` text COMMENT '评论内容',
  `score` int(11) DEFAULT NULL COMMENT '评星',
  `ispic` int(11) DEFAULT NULL COMMENT '是否有图片',
  `pics` varchar(2047) DEFAULT NULL COMMENT '点赞数',
  `helpfulnum` int(11) DEFAULT NULL COMMENT '点赞数',
  `sku` varchar(255) DEFAULT NULL COMMENT '评论的sku :  color  size   其他组合  Color: RoseGold | Size: 42mm/44mm ',
  `skuvalue` varchar(255) DEFAULT NULL COMMENT '评论的sku值 ： 红色   xl   32G红色 ',
  `is_recently` tinyint(4) DEFAULT '0' COMMENT '是否最新更新的数据',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新日期',
  `task_time` datetime DEFAULT NULL,
  `is_sync` tinyint(4) DEFAULT '0',
  `real_asin` varchar(125) DEFAULT NULL,
  `real_sku_asin` varchar(255) DEFAULT NULL,
  `real_comment_time` int(11) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `real_asin` (`real_asin`)
) ENGINE=MyISAM AUTO_INCREMENT=84752776 DEFAULT CHARSET=utf8mb4 COMMENT='商品评论表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asin_sync_utf8mb4`
--

DROP TABLE IF EXISTS `asin_sync_utf8mb4`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asin_sync_utf8mb4` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `url` varchar(2046) NOT NULL,
  `href` varchar(2048) DEFAULT NULL COMMENT 'the hypertext reference from which we can click on and lead to this page',
  `baseUri` varchar(2048) DEFAULT NULL COMMENT 'The url show in browser''s url bar when the page is open finally ',
  `label` varchar(32) DEFAULT NULL COMMENT 'multiple purpose label.',
  `category` varchar(255) DEFAULT NULL COMMENT '所属分类nodeID',
  `overviewbullets` varchar(2048) DEFAULT NULL COMMENT '简短的产品参数，在标题下',
  `detailbullets` text COMMENT '完整的产品参数，Product Information',
  `task_time` datetime DEFAULT NULL,
  `img` text COMMENT '主图URL',
  `imgsrc` text COMMENT '主图URL',
  `dynamicimgsrcs` text COMMENT '主图URL',
  `rank` text COMMENT '小类排名',
  `rank2` text COMMENT '小类排名',
  `smallrank` text COMMENT '小类排名',
  `bigrank` text COMMENT '大类排名',
  `title` text COMMENT '商品标题',
  `featurebullets` text COMMENT '商品描述',
  `desc` text COMMENT '商品描述',
  `asin` varchar(255) DEFAULT NULL COMMENT '商品asin编码',
  `pasin` varchar(255) DEFAULT NULL COMMENT '商品父asin编码',
  `totalvariations` varchar(64) DEFAULT NULL COMMENT '变体数',
  `jsVariables` text COMMENT 'All available javascript variables',
  `brand` varchar(1024) DEFAULT NULL COMMENT '品牌',
  `soldby` text COMMENT '卖家',
  `shipsfrom` text COMMENT 'Change shipby to shipsfrom to keep consistent with the official name. The old field shipby is deprecated, will remove later.',
  `quantity` varchar(255) DEFAULT NULL COMMENT 'Already change shipby to ships to keep consistent with the official name. This old field shipby is deprecated, will remove later.',
  `globaldeliverto` varchar(255) DEFAULT NULL COMMENT 'the destination to deliver to',
  `deliverto` varchar(255) DEFAULT NULL COMMENT 'the destination to deliver to',
  `price` text COMMENT '价格',
  `buyboxprice` text COMMENT '价格',
  `instock` varchar(1024) DEFAULT NULL COMMENT '是否缺货 0 不缺货  1  缺货',
  `isaddcart` tinyint(1) DEFAULT NULL COMMENT '加入购物车按钮',
  `isbuy` tinyint(1) DEFAULT NULL COMMENT '直接购买按钮',
  `isac` tinyint(1) DEFAULT NULL COMMENT '是否amazon精选推荐',
  `isbs` tinyint(1) DEFAULT NULL COMMENT '释放amazon热卖推荐',
  `iscoupon` tinyint(1) DEFAULT NULL COMMENT 'is coupon',
  `isprime` varchar(255) DEFAULT NULL COMMENT 'is prime',
  `isdeal` tinyint(1) DEFAULT NULL COMMENT 'is deal',
  `withdeal` varchar(255) DEFAULT NULL COMMENT 'You save',
  `yousave` varchar(255) DEFAULT NULL COMMENT 'You save',
  `detailimgs` text COMMENT 'all src of detail imgs',
  `detailvideos` text COMMENT 'all source of videos in product detail section',
  `isa` int(11) DEFAULT NULL COMMENT '是否A+页面',
  `boughttogethermetadata` text COMMENT 'all source of videos in product detail section',
  `boughttogetherimgs` text COMMENT 'all source of videos in product detail section',
  `boughttogether` text COMMENT 'all source of videos in product detail section',
  `othersellernum` int(11) DEFAULT NULL COMMENT '跟卖数量',
  `qanum` int(11) DEFAULT NULL COMMENT 'QA问题数',
  `stock_status` tinyint(4) DEFAULT '0' COMMENT '库存状态',
  `score` float DEFAULT NULL COMMENT '平均评星数',
  `reviews` int(11) DEFAULT NULL COMMENT '评论总数',
  `starnum` int(11) DEFAULT NULL COMMENT '评星总数',
  `score5percent` varchar(16) DEFAULT NULL COMMENT '5星级占比',
  `score4percent` varchar(16) DEFAULT NULL COMMENT '4星级占比',
  `score3percent` varchar(16) DEFAULT NULL COMMENT '3星级占比',
  `score2percent` varchar(16) DEFAULT NULL COMMENT '2星级占比',
  `score1percent` varchar(16) DEFAULT NULL COMMENT '1星级占比',
  `scoresbyfeature` varchar(16) DEFAULT NULL COMMENT 'Scores by feature',
  `weight` varchar(64) DEFAULT NULL COMMENT '重量',
  `volume` varchar(64) DEFAULT NULL COMMENT '体积',
  `isad` tinyint(1) DEFAULT NULL COMMENT '是否列表广告推广',
  `adposition` varchar(64) DEFAULT NULL COMMENT '列表广告位置',
  `commenttime` varchar(64) DEFAULT NULL COMMENT '第一条评论时间',
  `reviewsmention` text COMMENT '高频评论词',
  `onsaletime` varchar(64) DEFAULT NULL COMMENT '上架时间',
  `feedbackurl` text COMMENT '打开feedback页面的URL',
  `sellerID` text COMMENT 'sellerID',
  `marketplaceID` text COMMENT 'marketplaceID',
  `reviewsurl` text COMMENT '打开所有评论页面的URL',
  `sellsameurl` text COMMENT '打开跟卖信息页面的URL',
  `fba_fee` float DEFAULT NULL COMMENT 'FBA运费',
  `createtime` datetime DEFAULT CURRENT_TIMESTAMP,
  `isvalid` tinyint(1) DEFAULT '1' COMMENT 'valid',
  `categorylevel` text COMMENT '各级分类nodeID',
  `categorypath` text COMMENT '分类路径',
  `categorypathlevel` text COMMENT '各级分类路径',
  `categoryname` text COMMENT '分类名称',
  `categorynamelevel` text COMMENT '各级分类名称',
  `ranklevel` varchar(255) DEFAULT NULL COMMENT '各级排名',
  `brandlink` text COMMENT '品牌链接',
  `listprice` varchar(255) DEFAULT NULL COMMENT '挂牌价格',
  `gallery` text COMMENT '图库',
  `referer` text COMMENT 'The url which the url comes from',
  `numchars` varchar(16) DEFAULT NULL COMMENT 'Total number of text characters in this page',
  `numlinks` varchar(16) DEFAULT NULL COMMENT 'Total number of links in this page',
  `numimgs` varchar(16) DEFAULT NULL COMMENT 'Total number of images in this page',
  `height` varchar(16) DEFAULT NULL COMMENT 'Total number of images in this page',
  `is_sync` varchar(16) DEFAULT '0' COMMENT '是否同步 0 否 1 是',
  `source` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=5155567 DEFAULT CHARSET=utf8mb4 COMMENT='产品表，70+字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seller_sync`
--

DROP TABLE IF EXISTS `seller_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seller_sync` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `url` varchar(2047) DEFAULT NULL COMMENT 'The page url itself, it''s the same with seller_url',
  `href` varchar(2048) DEFAULT NULL COMMENT 'the hypertext reference from which we can click on and lead to this page',
  `label` varchar(32) DEFAULT NULL COMMENT 'multiple purpose label, e.g., a task id',
  `sellerID` varchar(255) DEFAULT NULL COMMENT 'sellerID',
  `seller_name` varchar(255) DEFAULT NULL COMMENT '卖家名称',
  `seller_url` varchar(2047) DEFAULT NULL COMMENT '卖家URL',
  `feedbackSummary` varchar(512) DEFAULT NULL COMMENT 'feedbackSummary',
  `business_name` varchar(512) DEFAULT NULL COMMENT 'business_name',
  `business_address` text COMMENT 'business_address',
  `marketplaceID` varchar(255) DEFAULT NULL COMMENT 'marketplaceID',
  `highstarpercent` varchar(255) DEFAULT NULL COMMENT '好评占比',
  `middlestarpercent` varchar(255) DEFAULT NULL COMMENT '中评占比',
  `badstarpercent` varchar(255) DEFAULT NULL COMMENT '差评占比',
  `feedback_num_12` varchar(255) DEFAULT NULL COMMENT '过去12月的评价数',
  `feedback_num` varchar(255) DEFAULT NULL COMMENT '评价总数',
  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新日期',
  `task_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=120574 DEFAULT CHARSET=utf8mb4 COMMENT='卖家feedback表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2021-02-06 18:50:11
