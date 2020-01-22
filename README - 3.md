```java
package xuwei.tech.streaming;

import org.apache.flink.api.common.functions.FoldFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import java.util.HashMap;
import java.util.Random;

public class GroupedProcessingTimeWindowSample {
    private static class DataSource extends RichParallelSourceFunction<Tuple2<String, Integer>> {
        private volatile boolean isRunning = true;

        @Override
        public void run(SourceContext<Tuple2<String, Integer>> ctx) throws Exception {
            Random random = new Random();
            while (isRunning) {
                Thread.sleep((getRuntimeContext().getIndexOfThisSubtask() + 1) * 1000 * 5);
                String key = "类别" + (char) ('A' + random.nextInt(3));
                int value = random.nextInt(10) + 1;

                System.out.println(String.format("Emits\t(%s, %d)", key, value));
                ctx.collect(new Tuple2<>(key, value));
            }
        }

        @Override
        public void cancel() {
            isRunning = false;
        }
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<Tuple2<String, Integer>> ds = env.addSource(new DataSource());
        KeyedStream<Tuple2<String, Integer>, Tuple> keyedStream = ds.keyBy(0);

        keyedStream.sum(1).keyBy(new KeySelector<Tuple2<String, Integer>, Object>() {
            @Override
            public Object getKey(Tuple2<String, Integer> stringIntegerTuple2) throws Exception {
                return "";
            }
        }).fold(new HashMap<String, Integer>(), new FoldFunction<Tuple2<String, Integer>, HashMap<String, Integer>>() {
            @Override
            public HashMap<String, Integer> fold(HashMap<String, Integer> accumulator, Tuple2<String, Integer> value) throws Exception {
                accumulator.put(value.f0, value.f1);
                return accumulator;
            }
        }).addSink(new SinkFunction<HashMap<String, Integer>>() {
            @Override
            public void invoke(HashMap<String, Integer> value, Context context) throws Exception {
                // 每个类型的商品成交量
                System.out.println("每个类型的商品成交量"+value);
                // 商品成交总量
                System.out.println("商品成交总量"+value.values().stream().mapToInt(v -> v).sum());
            }
        });

        env.execute();
    }
}
```
```

package com.huawei.camp.logmeasure.config;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 功能描述
 *
 * @since 2019-12-27
 */
@Component
@Configuration
@EnableElasticsearchRepositories(elasticsearchTemplateRef = "alphaElasticsearchTemplate")
public class AlphaElasticsearchConfig {
    @Value("${elasticsearch.alpha.cluster-nodes}")
    private String esClusterNodes;

    @Value("${elasticsearch.alpha.cluster-name}")
    private String esClusterName;

    @Value("${elasticsearch.alpha.cluster-port}")
    private int esClusterPort;

    @Bean
    public TransportClient alphaClient() throws Exception {

        Settings esSettings =
            Settings.builder().put("cluster.name", esClusterName).put("client.transport.sniff", false).build();

        return new PreBuiltTransportClient(esSettings).addTransportAddresses(getTransportAddresses());
    }

    @Bean(name = "alphaElasticsearchTemplate")
    public ElasticsearchTemplate alphaTemplate() throws Exception {
        return new ElasticsearchTemplate(alphaClient());
    }

    private TransportAddress[] getTransportAddresses() throws UnknownHostException {
        List<TransportAddress> list = new ArrayList<TransportAddress>();
        if (StringUtils.isNotBlank(esClusterNodes)) {
            String[] urlList = esClusterNodes.split(",");
            for (String str : urlList) {
                TransportAddress transportAddress = new TransportAddress(InetAddress.getByName(str), esClusterPort);
                list.add(transportAddress);
            }
        }
        return list.toArray(new TransportAddress[list.size()]);
    }
}

```
