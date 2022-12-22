# Requirement

1. 四个站点
   a. amazon.co.uk
   b. amazon.com
   c. amazon.de
   d. amazon.fr
   e. [Best-Sellers-202212](https://www.kdocs.cn/l/ca1IG5qylUel?openfrom=docs)
2. 【重点】商品详情页的字段确认
   a. “所有卖家、详情页创建或编辑者提交的信息，特别是页面上可见的任何描述产品的信息”
   b. 我们的默认方案里，已经包含了所有“卖家、详情页创建或编辑者提交的信息”，不过我们还需要双方确认一下是否有遗漏
   c. detailbullets/featurebullets/desc
   d. desc
   i. From the Brand
   ii. From the Manufacturer
   iii. Product Description
3. 其他注意事项
   a. 页面语言保持默认语言
   b. 价格单位保持默认单位
   c. 配送目的地保持默认还是切换一个？这个选择会影响库存、价格和运费
      i. Amazon.co.uk S99 3AD Amazon.com 30301 Amazon.de 10317 Amazon.fr 75008
4. 资源
   a. 样例字段标注看资源文件夹
   b. 样例链接：https://www.amazon.com/dp/B08DJ5B94G


com
===
No secondary best seller page | https://www.amazon.com/Best-Sellers-Industrial-Scientific-Medical-Respirator-Masks/zgbs/industrial/21265060011/ref=zg_bs_nav_industrial_5_16035056011
PATH_FETCHED_BEST_SELLER_URLS: file:///tmp/pulsar-com/report/fetch/fetched-best-sellers
providedPrimaryBestSellerUrls: 25
exportedPrimaryBestSellerUrls: 25
nonExportedPrimaryBestSellerUrls: 0
extractedSecondaryBestSellerUrls: 24
exportedSecondaryBestSellerUrls: 24
asinLinksInAllBestSellerPages: 2297
extractedASINPages: 2369

uk
===

No secondary best seller page | https://www.amazon.co.uk/Best-Sellers-Business-Industry-Science-Medical-Respirator-Masks/zgbs/industrial/21684390031
No secondary best seller page | https://www.amazon.co.uk/Best-Sellers-Business-Industry-Science-Surgical-Masks/zgbs/industrial/21726081031
PATH_FETCHED_BEST_SELLER_URLS: file:///tmp/pulsar-uk/report/fetch/fetched-best-sellers
providedPrimaryBestSellerUrls: 25
exportedPrimaryBestSellerUrls: 18
nonExportedPrimaryBestSellerUrls: 16
extractedSecondaryBestSellerUrls: 23
exportedSecondaryBestSellerUrls: 16
asinLinksInAllBestSellerPages: 1901
extractedASINPages: 1681

fr
===
No secondary best seller page | https://www.amazon.fr/gp/bestsellers/industrial/21726053031/ref=zg_bs_nav_industrial_5_21684424031
No ASIN link in best seller page | https://www.amazon.fr/gp/bestsellers/hpc/3162440031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.fr/gp/bestsellers/hpc/3160863031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.fr/gp/bestsellers/apparel/464831031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.fr/gp/bestsellers/sports/485936031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.fr/gp/bestsellers/sports/3055186031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.fr/gp/bestsellers/sports/339867031/ref=zg_bs_pg_2?ie=UTF8&pg=2
PATH_FETCHED_BEST_SELLER_URLS: file:///tmp/pulsar-fr/report/fetch/fetched-best-sellers
providedPrimaryBestSellerUrls: 24
exportedPrimaryBestSellerUrls: 24
nonExportedPrimaryBestSellerUrls: 0
extractedSecondaryBestSellerUrls: 23
exportedSecondaryBestSellerUrls: 17
asinLinksInAllBestSellerPages: 1923
extractedASINPages: 1739


de
===
No secondary best seller page | https://www.amazon.de/-/de/gp/bestsellers/industrial/6588990031/ref=zg_bs_nav_industrial_3_6588979031
No secondary best seller page | https://www.amazon.de/-/de/gp/bestsellers/industrial/21726057031/ref=zg_bs_nav_industrial_5_21691837031
No secondary best seller page | https://www.amazon.de/gp/bestsellers/industrial/21726056031/ref=zg_bs_nav_industrial_5_21691838031
No ASIN link in best seller page | https://www.amazon.de/-/de/gp/bestsellers/industrial/6588990031/ref=zg_bs_nav_industrial_3_6588979031
No ASIN link in best seller page | https://www.amazon.de/-/de/gp/bestsellers/industrial/21726057031/ref=zg_bs_nav_industrial_5_21691837031
No ASIN link in best seller page | https://www.amazon.de/gp/bestsellers/diy/2077678031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.de/gp/bestsellers/drugstore/2860130031/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.de/gp/bestsellers/sports/245332011/ref=zg_bs_pg_2?ie=UTF8&pg=2
No ASIN link in best seller page | https://www.amazon.de/gp/bestsellers/sports/3024175031/ref=zg_bs_pg_2?ie=UTF8&pg=2
PATH_FETCHED_BEST_SELLER_URLS: file:///tmp/pulsar-de/report/fetch/fetched-best-sellers
providedPrimaryBestSellerUrls: 25
exportedPrimaryBestSellerUrls: 23
nonExportedPrimaryBestSellerUrls: 2
extractedSecondaryBestSellerUrls: 22
exportedSecondaryBestSellerUrls: 18
asinLinksInAllBestSellerPages: 1969
extractedASINPages: 1602
