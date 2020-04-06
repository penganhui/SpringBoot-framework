### **案例代码：**使用reduce算子计算最大值

```
public class WindowsTest {
    public static void main(String[] args) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStreamSource<String> source1 = env.readTextFile("/Users/apple/Downloads/1.txt");
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SingleOutputStreamOperator<Row> stream1 = source1.map(new MapFunction<String, Row>() {
            @Override
            public Row map(String value) throws Exception {
                String[] split = value.split(",");
                String timeStamp = split[0];
                String name = split[1];
                int  score = Integer.parseInt(split[2]);
                Row row = new Row(3);
                row.setField(0,timeStamp);
                row.setField(1,name);
                row.setField(2,score);
                return row;
            }
        }).assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarks<Row>() {
             long  currentMaxTimestamp = 0L;
             long  maxOutOfOrderness = 10000L;
             Watermark watermark=null;
            //最大允许的乱序时间是10s
             @Nullable
             @Override
             public Watermark getCurrentWatermark() {
                watermark = new Watermark(currentMaxTimestamp - maxOutOfOrderness);
                 return watermark;
             }
             @Override
             public long extractTimestamp(Row element, long previousElementTimestamp) {
                 long timeStamp = 0;
                 try {
                     timeStamp = simpleDateFormat.parse(element.getField(0).toString()).getDate();
                 } catch (ParseException e) {
                     e.printStackTrace();
                 }
                 currentMaxTimestamp = Math.max(timeStamp, currentMaxTimestamp);
                     return timeStamp ;
             }
         }
        );
        stream1.keyBy(new KeySelector<Row, String>() {
            @Override
            public String getKey(Row value) throws Exception {
                return value.getField(1).toString();
            }
        }).window(TumblingEventTimeWindows.of(Time.seconds(5)))
                .reduce(new ReduceFunction<Row>() {
                    @Override
                    public Row reduce(Row value1, Row value2) throws Exception {
                        String s1 = value1.getField(2).toString();
                        String s2 = value2.getField(2).toString();
                        if(Integer.parseInt(s1)<Integer.parseInt(s2)){
                            return value2;
                        }else {
                            return value1;
                        }
                    }
                }).print();
 try {
            env.execute("test");
        } catch (Exception e) {
            e.printStackTrace();
        }
```

### 案例二：窗口内分组聚合：计算10秒中内各个单词的总数

 **注意：本案例采用的是处理时间，如果对数据要求有序请采用时间时间，写法参考案例一** 

```
public class GruopWc {
    public static void main(String[] args) throws Exception {
        //获取运行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //连接socket获取输入的数据
        DataStreamSource<String> text = env.socketTextStream("127.0.0.1", 3555);
        //计算数据
        DataStream<WordWithCount> windowCount = text.flatMap(new FlatMapFunction<String, WordWithCount>() {
            public void flatMap(String value, Collector<WordWithCount> out) throws Exception {
                String[] splits = value.split(",");
                for (String word : splits) {
                    out.collect(new WordWithCount(word, 1L));
                }
            }
        })//打平操作，把每行的单词转为<word,count>类型的数据
                //keyBy的时候可以指定多个ke进行分组
                .keyBy("word")//针对相同的word数据进行分组
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))//指定计算数据的窗口大小和滑动窗口大小
                .sum("count");
        //把数据打印到控制台
        windowCount.print().setParallelism(1);//使用一个并行度
        //注意：因为flink是懒加载的，所以必须调用execute方法，上面的代码才会执行
        env.execute("streaming word count");
    }
    /**
     * 主要为了存储单词以及单词出现的次数
     */
    public static class WordWithCount {
        public String word;
        public long count;
        public WordWithCount() {
        }
        public WordWithCount(String word, long count) {
            this.word = word;
            this.count = count;
        }
        @Override
        public String toString() {
            return "WordWithCount{" + "word='" + word + '\'' + ", count=" + count + '}';
        }
    }
}
```
```

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2019-2019. All rights reserved.
 */

package com.huawei.camp.logmeasure.controller;

import org.elasticsearch.common.joda.DateMathParser;
import java.util.function.LongSupplier;

import org.elasticsearch.common.joda.Joda;
import org.joda.time.LocalDate;
import org.joda.time.Days;
import org.joda.time.DurationFieldType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ScrolledPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.camp.logmeasure.entity.LogEntity;
import com.huawei.camp.logmeasure.entity.LogServiceNameEntity;
import com.huawei.camp.logmeasure.entity.ZuulLogResultData;
import com.huawei.camp.logmeasure.utils.ResponesCodeEnum;
import com.huawei.camp.logmeasure.utils.Response;
import com.huawei.camp.logmeasure.utils.ResultTools;

import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * Elasticsearch查询类
 *
 * @author fWX806764
 * @since 2019-10-29
 */
@RestController
@RequestMapping(value = "/v1", produces = {"application/json;charset=UTF-8"})
public class ZuulLogController {
    private Logger logger = LoggerFactory.getLogger(ZuulLogController.class);
    @Resource(name = "alphaElasticsearchTemplate")
    private ElasticsearchTemplate alphaElasticsearchTemplate;
    @Resource(name = "betaElasticsearchTemplate")
    private ElasticsearchTemplate betaElasticsearchTemplate;
    @Resource(name = "gammaElasticsearchTemplate")
    private ElasticsearchTemplate gammaElasticsearchTemplate;
    @Resource(name = "prodElasticsearchTemplate")
    private ElasticsearchTemplate prodElasticsearchTemplate;

    /**
     * 获取es中的全部键
     *
     * @return Result
     */
    @GetMapping(value = "/accesslog-stats")
    @ApiOperation(value = "zull日志统计", notes = "", httpMethod = "GET", response = String.class)
    @ApiImplicitParams({@ApiImplicitParam(dataType = "string", name = "serviceName", value = "服务名称"),
            @ApiImplicitParam(dataType = "string", name = "originIp", value = "用户ip"),
            @ApiImplicitParam(dataType = "long", name = "startTime", value = "开始时间", example = "0"),
            @ApiImplicitParam(dataType = "long", name = "endTime", value = "结束时间", example = "0"),
            @ApiImplicitParam(dataType = "int", name = "pageno", value = "页数", required = true, example = "1"),
            @ApiImplicitParam(dataType = "int", name = "pagesize", value = "页码", required = true, example = "10")
    })
    public Response getAllKeys(String serviceName, String originIp,
                               long startTime, long endTime,
                               int pageno, int pagesize) {
        Response responeData;
        try {
            BoolQueryBuilder boolQueryBuilder = boolQuery();
            if (StringUtils.isNotBlank(serviceName)) {
                boolQueryBuilder.must(QueryBuilders.wildcardQuery("serviceName.keyword", "*" + serviceName + "*"));
            }
            if (StringUtils.isNotBlank(originIp)) {
                boolQueryBuilder.must(termQuery("originIp.keyword", originIp));
            }
            if (startTime > 0 && endTime > 0) {
                boolQueryBuilder.must(QueryBuilders.rangeQuery("startTS").gte(startTime).lte(endTime));
            }
            SearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(boolQueryBuilder)
                    .withPageable(PageRequest.of(pageno - 1, pagesize)).build();
            ScrolledPage<LogEntity> scroll = (ScrolledPage<LogEntity>) alphaElasticsearchTemplate
                    .startScroll(3000, searchQuery, LogEntity.class);
            List<LogEntity> listData = alphaElasticsearchTemplate.queryForList(searchQuery, LogEntity.class);
            ZuulLogResultData resultData = new ZuulLogResultData();
            resultData.setList(listData);
            resultData.setTotal(scroll.getTotalElements());
            responeData = ResultTools.success(resultData);
        } catch (Exception e) {
            responeData = ResultTools.fail(ResponesCodeEnum.ERROR.getMsg(), ResponesCodeEnum.ERROR.getCode());
        }
        return responeData;
    }

    /**
     * 获取es中serviceName统计
     *
     * @return Result
     */
    @GetMapping(value = "/service-accesslog-stats")
    @ApiOperation(value = "zull serviceName日志统计", notes = "", httpMethod = "GET", response = String.class)
    @ApiImplicitParams({
            @ApiImplicitParam(dataType = "string", name = "environmentType", required = true, value = "环境类型(alpha-camp-zuul、" +
                    "beta-camp-zuul、gamma-camp-zuul、camp-zuul)"),
            @ApiImplicitParam(dataType = "string", name = "queryType", required = true, value = "查询类型(min、hour、day)"),
            @ApiImplicitParam(dataType = "long", name = "startTime", required = true, value = "开始时间", example = "0"),
            @ApiImplicitParam(dataType = "long", name = "endTime", required = true, value = "结束时间", example = "0")
    })
    public Response getServiceNameKeys(String environmentType, String queryType, long startTime, long endTime) {
        long currentTimeMillis = System.currentTimeMillis();
        Map<String, Object> esInfos = getEsIndexName(queryType,currentTimeMillis);
        long queryTime = (long) esInfos.get("queryTime");
        Object timeType = esInfos.get("queryType");
        Response responeData;
        try {
            BoolQueryBuilder boolQueryBuilder = boolQuery();
            boolQueryBuilder.filter(termQuery("type.keyword", "response"));
            String[] multipleIndexs;
            if (startTime > 0 && endTime > 0) {
                multipleIndexs = getMultipleIndexs(String.valueOf(startTime), String.valueOf(endTime), environmentType);
                boolQueryBuilder.must(QueryBuilders.rangeQuery("currentDateTime").gte(startTime).lte(endTime));
            } else {
                multipleIndexs = getMultipleIndexs(String.valueOf(queryTime), String.valueOf(currentTimeMillis), environmentType);
                boolQueryBuilder.must(QueryBuilders.rangeQuery("currentDateTime").gte(queryTime).lte(currentTimeMillis));
            }
            // 聚合查询配置
            //根据时间分组统计总数
            DateHistogramAggregationBuilder fieldBuilder = AggregationBuilders.dateHistogram("currentDateTime")
                    .field("currentDateTime").dateHistogramInterval((DateHistogramInterval) timeType)
                    .format("epoch_millis");

            TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("agg_serviceName")
                    .field("serviceName.keyword").size(1000);
            SearchQuery searchQuery = new NativeSearchQueryBuilder().withIndices(multipleIndexs).withTypes("doc")
                    .withQuery(boolQueryBuilder).addAggregation(fieldBuilder.subAggregation(termsAggregationBuilder))
                    .withPageable(PageRequest.of(0, 60))
                    .build();
            Aggregations aggregations = null;
            if ("alpha-camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = alphaElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            if ("beta-camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = betaElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            if ("gamma-camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = gammaElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            if ("camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = prodElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }

            Map<String, Aggregation> aggregationMap = aggregations.asMap();
            Histogram count = (Histogram) aggregationMap.get("currentDateTime");
            List<Map<String, Object>> dataList = new ArrayList<>();
            List<String> serviceNames = new ArrayList<>();
            Map<String, Object> timemap = new HashMap<>();
            for (Histogram.Bucket bucket : count.getBuckets()) {
                Map<String, Object> dataMap = new HashMap<>();
                DateTime dateTime = (DateTime) bucket.getKey();
                long millis = dateTime.getMillis();
                //设置有数据的时间
                Terms terms = bucket.getAggregations().get("agg_serviceName");
                for (Terms.Bucket bucket1 : terms.getBuckets()) {
                    dataMap.put(bucket1.getKey().toString(), bucket1.getDocCount());
                    if (!serviceNames.contains(bucket1.getKey())) {
                        serviceNames.add(bucket1.getKey().toString());
                    }
                }
                timemap.put(String.valueOf(millis), dataMap);
            }
            dataList.add(timemap);
            ZuulLogResultData resultData = new ZuulLogResultData();
            resultData.setList(dataList);
            resultData.setServiceNames(serviceNames);
            resultData.setTotal(timemap.size());
            responeData = ResultTools.success(resultData);
        } catch (Exception e) {
            responeData = ResultTools.fail(ResponesCodeEnum.ERROR.getMsg(), ResponesCodeEnum.ERROR.getCode());
            logger.info(e.getMessage());
        }
        return responeData;
    }

    /**
     * 获取es中以serviceName为维度统计originIp的访问次数
     *
     * @return Result
     */
    @GetMapping(value = "/servicepath-accesslog-stats")
    @ApiOperation(value = "zull originIp访问次数统计", notes = "", httpMethod = "GET", response = String.class)
    @ApiImplicitParams({
            @ApiImplicitParam(dataType = "string", name = "environmentType", required = true, value = "环境类型(alpha-camp-zuul、" +
                    "beta-camp-zuul、gamma-camp-zuul、camp-zuul)"),
            @ApiImplicitParam(dataType = "string", name = "queryType", required = true, value = "查询类型(min、hour、day)"),
            @ApiImplicitParam(dataType = "string", name = "serviceName", required = true, value = "服务名称"),
            @ApiImplicitParam(dataType = "long", name = "startTime", required = true, value = "开始时间", example = "0"),
            @ApiImplicitParam(dataType = "long", name = "endTime", required = true, value = "结束时间", example = "0")
    })
    public Response getServicePaths(String environmentType, String queryType, String serviceName, long startTime, long endTime) {
        long currentTimeMillis = System.currentTimeMillis();
        Map<String, Object> mapInfos = getEsIndexName(queryType,currentTimeMillis);
        long queryTime = (long) mapInfos.get("queryTime");
        Response responeData;
        try {
            BoolQueryBuilder boolQueryBuilder = boolQuery();
            boolQueryBuilder.filter(termQuery("type.keyword", "response"));
            String[] multipleIndexs;
            if (startTime > 0 && endTime > 0) {
                multipleIndexs = getMultipleIndexs(String.valueOf(startTime), String.valueOf(endTime), environmentType);
                boolQueryBuilder.must(QueryBuilders.rangeQuery("currentDateTime").gte(startTime).lte(endTime));
            } else {
                boolQueryBuilder.must(QueryBuilders.rangeQuery("currentDateTime").gte(queryTime).lte(currentTimeMillis));
                multipleIndexs = getMultipleIndexs(String.valueOf(currentTimeMillis), String.valueOf(currentTimeMillis), environmentType);
            }
            if (StringUtils.isNotBlank(serviceName)) {
                boolQueryBuilder.must(QueryBuilders.wildcardQuery("serviceName.keyword", serviceName));
            }

            // 聚合查询配置
            //根据时间分组统计总数
//            DateHistogramAggregationBuilder fieldBuilder = AggregationBuilders.dateHistogram("currentDateTime")
//                    .field("currentDateTime").dateHistogramInterval((DateHistogramInterval) timeType)
//                    .format("epoch_millis");

            TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("agg_originIp")
                    .field("originIp.keyword").order(BucketOrder.count(false)).size(10);
            SearchQuery searchQuery = new NativeSearchQueryBuilder().withIndices(multipleIndexs).withTypes("doc")
                    .withQuery(boolQueryBuilder).addAggregation(termsAggregationBuilder)
//                    .withPageable(PageRequest.of(0, 100))
                    .build();
            Aggregations aggregations = null;
            if ("alpha-camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = alphaElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            if ("beta-camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = betaElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            if ("gamma-camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = gammaElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            if ("camp-zuul".equalsIgnoreCase(environmentType)) {
                aggregations = prodElasticsearchTemplate.query(searchQuery, SearchResponse::getAggregations);
            }
            Map<String, Aggregation> aggregationMap = aggregations.asMap();
            Terms terms = (Terms) aggregationMap.get("agg_originIp");
            List dataList = new ArrayList();
            for (Terms.Bucket bucket : terms.getBuckets()) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("ip", bucket.getKey());
                dataMap.put("count", bucket.getDocCount());
                dataList.add(dataMap);
            }
            ZuulLogResultData resultData = new ZuulLogResultData();
            resultData.setList(dataList);
            resultData.setTotal(dataList.size());
            responeData = ResultTools.success(resultData);
        } catch (Exception e) {
            logger.info(e.getMessage());
            responeData = ResultTools.fail(ResponesCodeEnum.ERROR.getMsg(), ResponesCodeEnum.ERROR.getCode());
        }
        return responeData;
    }


    /**
     * list数据去重
     *
     * @param list
     * @return
     */
    public List removeDuplicate(List<LogServiceNameEntity> list) {
        List listTemp = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            if (!listTemp.contains(list.get(i).getServiceName())) {
                listTemp.add(list.get(i).getServiceName());
            }
        }
        return listTemp;
    }

    /**
     * 根据不通类型查询ES indexName
     *
     * @param queryType
     * @return
     */
    public Map<String, Object> getEsIndexName(String queryType,long currentTimeMillis) {
        Map<String, Object> map = new HashMap<>();
        long queryTime = 0;
        if ("min".equals(queryType)) {
            DateHistogramInterval minute = DateHistogramInterval.MINUTE;
            map.put("queryType", minute);
            queryTime = currentTimeMillis - 30 * 60 * 1000L;//最近30分钟数据
        }
        if ("hour".equals(queryType)) {
            DateHistogramInterval hour = DateHistogramInterval.HOUR;
            map.put("queryType", hour);
            queryTime = currentTimeMillis - 24 * 60 * 60 * 1000L;//最近24小时数据
        }
        if ("day".equals(queryType)) {
            DateHistogramInterval day = DateHistogramInterval.DAY;
            map.put("queryType", day);
            queryTime = currentTimeMillis - 30 * 24 * 60 * 60 * 1000L;//最近30天数据
        }
        map.put("queryTime", queryTime);
        return map;
    }

    /**
     * 根据起始时间和结束时间拼接多个ES indexName
     *
     * @param startTime
     * @param endTime
     * @return
     */
    public String[] getMultipleIndexs(String startTime, String endTime, String environmentType) {
        DateMathParser parser = new DateMathParser(Joda.forPattern("date_optional_time||epoch_millis"));
        LongSupplier supplier = () -> new Date().getTime();
        LocalDate startDate = new LocalDate(parser.parse(startTime, supplier));
        LocalDate endDate = new LocalDate(parser.parse(endTime, supplier));
        int days = Days.daysBetween(startDate, endDate).getDays() + 1;
        ArrayList<String> indexes = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.withFieldAdded(DurationFieldType.days(), i);
            String indexName = environmentType+"-" + date.toString("YYYY.MM.dd");
            indexes.add(indexName);
        }

        return indexes.toArray(new String[days]);
    }
}

```
