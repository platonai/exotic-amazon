Seeds
=

There is a program to crawl the entire amazon category tree, and generates the seed files.

The format of the seed file is:

```kotlin
val format = String.format(
"%s | %s | %s | %s | %s | %s\n",
"id", "parent id", "depth", "numSubcategories", "category path", "url"
)
```

We can rebuild the category tree from the file.
