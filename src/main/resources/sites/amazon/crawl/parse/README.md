## Scrape all entities in a product page

All X-SQLs can be find [here](sql/crawl).

    POST http://crawl0:8182/api/x/a/v2/q
    Content-Type: application/json

    {
      "authToken": "rhlwTRBk-1-de14124c7ace3d93e38a705bae30376c",
      "url": "https://item.jd.com/10023068375912.html",
      "args": "-i 0s",
      "sqls": {
        "asin": "{{asin.x.sql}}",
        "sims-1": "{{x-asin-sims-consolidated-1.sql}}",
        "sims-2": "{{x-asin-sims-consolidated-2.sql}}",
        "sims-3": "{{x-asin-sims-consolidated-3.sql}}",
        "sims-consider": "{{x-asin-sims-consider.sql}}",
        "top-reviews": "{{x-asin-top-reviews.sql}}"
      },
      "callbackUrl": "http://localhost:8182/api/hello/echo",
      "priority": "HIGHER2"
    }

Note: 

    1. replace authToken to your own authToken
    2. replace {{xxx.sql}} to be the real sql
    3. replace callbackUrl to your own callbackUrl
    4. x-similar-items.sql is another entity but not supported currently

## API Usage

### Request format

#### Pure HTTP

    POST http://crawl0:8182/api/x/a/v2/q
    Content-Type: application/json

    {
      "authToken": "rhlwTRBk-1-de14124c7ace3d93e38a705bae30376c",
      "url": "https://item.jd.com/10023068375912.html",
      "args": "-i 0s",
      "sqls": {
        "urls": " select dom_base_uri(dom) as uri from load_and_select(@url, ':root')",
        "titles": " select dom_doc_title(dom) as page_title, dom_first_text(dom, '.sku-name') as title, dom_first_text(dom, '.p-price') as price from load_and_select(@url, ':root')",
        "metadata": " select dom_first_attr(dom, 'head meta[name=keywords]', 'content') as meta_keywords, dom_first_attr(dom, 'head meta[name=description]', 'content') as meta_description from load_and_select(@url, ':root')"
      },
      "callbackUrl": "http://localhost:8182/api/hello/echo",
      "priority": "HIGHER2"
    }

#### Kotlin

    data class ScrapeRequestV2(
        val authToken: String,
        val url: String,
        val args: String? = null,
        val sqls: Map<String, String> = mutableMapOf(),
        val callbackUrl: String? = null,
        var priority: String = Priority13.HIGHER2.name
    )

??????????????? SQL ????????????????????????????????????URL ????????? @url ?????????????????????????????????????????? @url ????????? 'https://item.jd.com/10023068375912.html' ??????????????????

### Response format

#### Pure HTTP

    GET http://crawl0:8182/api/x/a/v2/status?uuid=9146ede0-a2ab-4682-a5ec-a71b3f540a19&authToken=EhYA3fEH-1-08b93817a25445a1ea78f337683a25e5

    {
      "uuid": "9146ede0-a2ab-4682-a5ec-a71b3f540a19",
      "statusCode": 200,
      "pageStatusCode": 200,
      "pageContentBytes": 280598,
      "resultSets": [
        {
          "name": "urls",
          "records": [
            {
              "uri": "https://item.jd.com/10023068375912.html"
            }
          ]
        },
        {
          "name": "titles",
          "records": [
            {
              "page_title": "Apple ?????? iPhone 12mini ?????????5G?????? ?????? 128GB????????? ?????? ?????? ?????????-??????",
              "title": "Apple ?????? iPhone 12mini ?????????5G?????? ?????? 128GB",
              "price": "??? 5738.00"
            }
          ]
        },
        {
          "name": "metadata",
          "records": [
            {
              "meta_keywords": "Apple ?????? iPhone 12mini ?????????5G?????? ?????? 128GB,Apple,,??????,????????????",
              "meta_description": "Apple ?????? iPhone 12mini ?????????5G?????? ?????? 128GB??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"
            }
          ]
        }
      ],
      "status": "OK",
      "pageStatus": "OK",
      "version": "20210312",
      "timestamp": "2021-03-13T13:43:44.327070Z"
    }

#### Kotlin

    data class ScrapeResponseV2(
        val uuid: String,
        var statusCode: Int = ResourceStatus.SC_CREATED,
        var pageStatusCode: Int = ProtocolStatusCodes.CREATED,
        var pageContentBytes: Int = 0,
        var resultSets: MutableList<ScrapeResultSet>? = mutableListOf()
    ) {
        val status: String = ResourceStatus.getStatusText(statusCode)
        val pageStatus: String = ProtocolStatus.getMinorName(pageStatusCode)
        val version: String = "20210312"
        val timestamp: String = Instant.now().toString()
    }

    data class ScrapeResultSet(
        val name: String,
        var records: List<Map<String, Any?>>? = null
    )
