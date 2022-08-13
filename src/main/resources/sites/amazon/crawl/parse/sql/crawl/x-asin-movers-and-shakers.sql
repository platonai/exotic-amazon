-- noinspection SqlResolveForFile
-- noinspection SqlNoDataSourceInspectionForFile

select
    dom_base_uri(dom) as url,
    amazon_find_asin(dom_first_href(dom, 'a[href~=/dp/]')) as asin,
    dom_first_href(dom, 'a[href~=/dp/]') as `asin_url`,
    str_substring_between(dom_base_uri(dom), 'movers-and-shakers/', '/') as `categoryinurl`,
    array_join_to_string(dom_all_texts(dom_owner_body(dom), 'div[role=tree] div[class~=browse-up]'), ' > ') as categorylevel,
    dom_first_text(dom_owner_body(dom), 'div[role=tree] div[role=treeitem] span[class~=selected]') as selectedcategory,
    dom_base_uri(dom) as `category_url`,
    dom_first_integer(dom, 'span.zg-bdg-text', 0) as rank,
    dom_first_text(dom, 'span:containsOwn(offers from)') as `offers`,
    dom_first_text(dom, '.p13n-sc-price') as `price`,
    dom_first_text(dom, 'a:expr(img=0 && char>30)') as `title`,
    dom_first_attr(dom, 'span.zg-item div img[src]', 'src') as `pic`,
    str_substring_before(dom_first_attr(dom, 'div[id~=B0] a[title~=stars]', 'title'), ' out') as score,
    dom_first_text(dom, 'div[id~=B0] a[title~=stars] i ~ span') as starnum,
    dom_first_text(dom, 'span.zg-percent-change') as `sales_rank_change`,
    dom_first_text(dom, 'span.zg-sales-movement') as `sales_rank`,
    time_first_mysql_date_time(dom_attr(dom_select_first(dom_owner_body(dom), '#PulsarMetaInformation'), 'taskTime')) as `task_time`
from load_and_select(@url, 'div.p13n-gridRow > div[id~=Item]');
