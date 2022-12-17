Parse & extract system
=

All fields are extracted using X-SQLs, which can be found [here](sql/crawl).

Extract config
==

Extract rule explained:

```text
"id": 1,                                # the extractor id
"name": "asin",                         # the unique extractor name
"urlPattern": ".+/dp/.+",               # pattern of urls to extract
"minContentSize": 500000,               # minimal page content size
"minNumNonBlankFields": 20,             # minimal number of non-blank fields
"sqlTemplate": "x-asin.sql",            # the SQL template to extract fields
"collection": "asin_sync"               # the destination collection to push extract fields
```
