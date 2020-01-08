package com.lj.zby.controller;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@RestController
public class ElasticsearController {
    @Autowired
    ElasticsearchTemplate elasticsearchTemplate;

    public void getEsInfo(){
        BoolQueryBuilder boolQueryBuilder = boolQuery();
        boolQueryBuilder.filter(termQuery("type.keyword",""));
        if (StringUtils.isEmpty("")){
            boolQueryBuilder.must(QueryBuilders.wildcardQuery("name.keyword","value"));//通配符查询*name*
        }
        boolQueryBuilder.must(QueryBuilders.rangeQuery("time").gte("from").lte("to"));//范围查询gte大于等于lte小于等于
        //根据时间按 分 时 天统计数据
        DateHistogramAggregationBuilder builder = AggregationBuilders.dateHistogram("别名").field("字段")
                .dateHistogramInterval(DateHistogramInterval.DAY).format("epoch millis");//epoch millis时间戳显示
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("别名").field("key.keyword").size(10);
        SearchQuery searchQuery = new NativeSearchQueryBuilder().withIndices("indexs支持多个引号外逗号").withTypes("doc")
                .withQuery(boolQueryBuilder).addAggregation(builder.subAggregation(termsAggregationBuilder))
                .withPageable(PageRequest.of(0,10)).build();
//        Aggregations aggregations = elasticsearchTemplate.query(searchQuery,searchResponse::getAggregations);
//        Map<String,Aggregation> aggregationMap = aggregations.asMap();
//        Histogram histogram = aggregationMap.get("别名");
//        for (Histogram.Bucket bucket:histogram.getBuckets()) {
//            DateTime dateTime = (DateTime)bucket.getKey();
//
//        }
    }
}
