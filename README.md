# SpringBoot-framework
myself frame

“flatMap”类似于把一个记录拆分成两条、三条、甚至是四条记录,例如把一个字符串分割成一个字符数组。

“Filter”就类似于过滤。

“keyBy”就等效于SQL里的group by。

“aggregate”是一个聚合操作，如计数、求和、求平均等。

“reduce”就类似于MapReduce里的reduce。

“join”操作就有点类似于我们数据库里面的join。

“connect”实现把两个流连成一个流。

“repartition”是一个重新分区操作（还没研究）。

“project”操作就类似于SQL里面的snacks（还没研究）。

常见的操作有filter、map、flatMap、keyBy(分组)、aggregate(聚合)
 具体的使用方式后面的例子中会体现。



### 编写程序

在 `src/main/java/myflink` 下创建 `HotItems.java` 文件： 

```
package myflink;

public class HotItems {

    public static void main(String[] args) throws Exception {

    }
}
```

 与上文一样，我们会一步步往里面填充代码。第一步仍然是创建一个 `StreamExecutionEnvironment`，我们把它添加到 main 函数中。 

```
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
// 为了打印到控制台的结果不乱序，我们配置全局的并发为1，这里改变并发对结果正确性没有影响
env.setParallelism(1);
```

###创建模拟数据源

在数据准备章节，我们已经将测试的数据集下载到本地了。由于是一个csv文件，我们将使用 `CsvInputFormat` 创建模拟数据源。 

```
注：虽然一个流式应用应该是一个一直运行着的程序，需要消费一个无限数据源。但是在本案例教程中，为了省去构建真实数据源的繁琐，我们使用了文件来模拟真实数据源，这并不影响下文要介绍的知识点。这也是一种本地验证 Flink 应用程序正确性的常用方式。
```

我们先创建一个 `UserBehavior` 的 POJO 类（所有成员变量声明成`public`便是POJO类），强类型化后能方便后续的处理。 

```
/** 用户行为数据结构 **/
public static class UserBehavior {
    public long userId;         // 用户ID
    public long itemId;         // 商品ID
    public int categoryId;      // 商品类目ID
    public String behavior;     // 用户行为, 包括("pv", "buy", "cart", "fav")
    public long timestamp;      // 行为发生的时间戳，单位秒
}
```

 接下来我们就可以创建一个 `PojoCsvInputFormat` 了， 这是一个读取 csv 文件并将每一行转成指定 POJO 类型（在我们案例中是 `UserBehavior`）的输入器。 

```
// UserBehavior.csv 的本地文件路径
URL fileUrl = HotItems2.class.getClassLoader().getResource("UserBehavior.csv");
Path filePath = Path.fromLocalFile(new File(fileUrl.toURI()));
// 抽取 UserBehavior 的 TypeInformation，是一个 PojoTypeInfo
PojoTypeInfo pojoType = (PojoTypeInfo) TypeExtractor.createTypeInfo(UserBehavior.class);
// 由于 Java 反射抽取出的字段顺序是不确定的，需要显式指定下文件中字段的顺序
String[] fieldOrder = new String[]{"userId", "itemId", "categoryId", "behavior", "timestamp"};
// 创建 PojoCsvInputFormat
PojoCsvInputFormat csvInput = new PojoCsvInputFormat<>(filePath, pojoType, fieldOrder);
```

 下一步我们用 `PojoCsvInputFormat` 创建输入源。 

```
DataStream dataSource = env.createInput(csvInput, pojoType);
```

 这就创建了一个 `UserBehavior` 类型的 `DataStream`。 

#### EventTime 与 Watermark

当我们说“统计过去一小时内点击量”，这里的“一小时”是指什么呢？ 在 Flink 中它可以是指 ProcessingTime ，也可以是 EventTime，由用户决定。

- ProcessingTime：事件被处理的时间。也就是由机器的系统时间来决定。
- EventTime：事件发生的时间。一般就是数据本身携带的时间。

 在本案例中，我们需要统计业务时间上的每小时的点击量，所以要基于 EventTime 来处理。那么如果让 Flink 按照我们想要的业务时间来处理呢？这里主要有两件事情要做。

第一件是告诉 Flink 我们现在按照 EventTime 模式进行处理，Flink 默认使用 ProcessingTime 处理，所以我们要显式设置下。

 ```java
env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
 ```

第二件事情是指定如何获得业务时间，以及生成 Watermark。Watermark 是用来追踪业务事件的概念，可以理解成 EventTime 世界中的时钟，用来指示当前处理到什么时刻的数据了。由于我们的数据源的数据已经经过整理，没有乱序，即事件的时间戳是单调递增的，所以可以将每条数据的业务时间就当做 Watermark。这里我们用 `AscendingTimestampExtractor` 来实现时间戳的抽取和 Watermark 的生成。

 注：真实业务场景一般都是存在乱序的，所以一般使用 

`BoundedOutOfOrdernessTimestampExtractor`。 

 ```java
DataStream timedData = dataSource
    .assignTimestampsAndWatermarks(new AscendingTimestampExtractor() {
        @Override
        public long extractAscendingTimestamp(UserBehavior userBehavior) {
            // 原始数据单位秒，将其转成毫秒
            return userBehavior.timestamp * 1000;
        }
    });
 ```

这样我们就得到了一个带有时间标记的数据流了，后面就能做一些窗口的操作。 

###过滤出点击事件

 在开始窗口操作之前，先回顾下需求“每隔5分钟输出过去一小时内**点击量**最多的前 N 个商品”。由于原始数据中存在点击、加购、购买、收藏各种行为的数据，但是我们只需要统计点击量，所以先使用 `FilterFunction` 将点击行为数据过滤出来。

 ```java
DataStream pvData = timedData
    .filter(new FilterFunction() {
        @Override
        public boolean filter(UserBehavior userBehavior) throws Exception {
            // 过滤出只有点击的数据
            return userBehavior.behavior.equals("pv");
        }
    });
 ```

#### 窗口统计点击量

 由于要每隔5分钟统计一次最近一小时每个商品的点击量，所以窗口大小是一小时，每隔5分钟滑动一次。即分别要统计 [09:00, 10:00), [09:05, 10:05), [09:10, 10:10)... 等窗口的商品点击量。是一个常见的滑动窗口需求（Sliding Window）。

 ```java
DataStream windowedData = pvData
    .keyBy("itemId")
    .timeWindow(Time.minutes(60), Time.minutes(5))
    .aggregate(new CountAgg(), new WindowResultFunction());
 ```

我们使用`.keyBy("itemId")`对商品进行分组，使用`.timeWindow(Time size, Time slide)`对每个商品做滑动窗口（1小时窗口，5分钟滑动一次）。然后我们使用 `.aggregate(AggregateFunction af, WindowFunction wf)` 做增量的聚合操作，它能使用`AggregateFunction`提前聚合掉数据，减少 state 的存储压力。较之`.apply(WindowFunction wf)`会将窗口中的数据都存储下来，最后一起计算要高效地多。`aggregate()`方法的第一个参数用于

这里的`CountAgg`实现了`AggregateFunction`接口，功能是统计窗口中的条数，即遇到一条数据就加一。

 ```java
/** COUNT 统计的聚合函数实现，每出现一条记录加一 */
public static class CountAgg implements AggregateFunction {

    @Override
    public Long createAccumulator() {
        return 0L;
    }

    @Override
    public Long add(UserBehavior userBehavior, Long acc) {
        return acc + 1;
    }

    @Override
    public Long getResult(Long acc) {
        return acc;
    }

    @Override
    public Long merge(Long acc1, Long acc2) {
        return acc1 + acc2;
    }
}
 ```

`.aggregate(AggregateFunction af, WindowFunction wf)` 的第二个参数`WindowFunction`将每个 key每个窗口聚合后的结果带上其他信息进行输出。我们这里实现的`WindowResultFunction`将主键商品ID，窗口，点击量封装成了`ItemViewCount`进行输出。

 ```java
/** 用于输出窗口的结果 */
public static class WindowResultFunction implements WindowFunction {

    @Override
    public void apply(
            Tuple key,  // 窗口的主键，即 itemId
            TimeWindow window,  // 窗口
            Iterable aggregateResult, // 聚合函数的结果，即 count 值
            Collector collector  // 输出类型为 ItemViewCount
    ) throws Exception {
        Long itemId = ((Tuple1) key).f0;
        Long count = aggregateResult.iterator().next();
        collector.collect(ItemViewCount.of(itemId, window.getEnd(), count));
    }
}

/** 商品点击量(窗口操作的输出类型) */
public static class ItemViewCount {
    public long itemId;     // 商品ID
    public long windowEnd;  // 窗口结束时间戳
    public long viewCount;  // 商品的点击量

    public static ItemViewCount of(long itemId, long windowEnd, long viewCount) {
        ItemViewCount result = new ItemViewCount();
        result.itemId = itemId;
        result.windowEnd = windowEnd;
        result.viewCount = viewCount;
        return result;
    }
}
 ```

现在我们得到了每个商品在每个窗口的点击量的数据流。 

#### TopN 计算最热门商品

为了统计每个窗口下最热门的商品，我们需要再次按窗口进行分组，这里根据`ItemViewCount`中的`windowEnd`进行`keyBy()`操作。然后使用 `ProcessFunction` 实现一个自定义的 TopN 函数 `TopNHotItems` 来计算点击量排名前3名的商品，并将排名结果格式化成字符串，便于后续输出。

 ```
DataStream topItems = windowedData
    .keyBy("windowEnd")
    .process(new TopNHotItems(3));  // 求点击量前3名的商品
 ```

`ProcessFunction` 是 Flink 提供的一个 low-level API，用于实现更高级的功能。它主要提供了定时器 timer 的功能（支持EventTime或ProcessingTime）。本案例中我们将利用 timer 来判断何时**收齐**了某个 window 下所有商品的点击量数据。由于 Watermark 的进度是全局的，

在 `processElement` 方法中，每当收到一条数据（`ItemViewCount`），我们就注册一个 `windowEnd+1` 的定时器（Flink 框架会自动忽略同一时间的重复注册）。`windowEnd+1` 的定时器被触发时，意味着收到了`windowEnd+1`的 Watermark，即收齐了该`windowEnd`下的所有商品窗口统计值。我们在 `onTimer()` 中处理将收集的所有商品及点击量进行排序，选出 TopN，并将排名信息格式化成字符串后进行输出。

这里我们还使用了 `ListState<ItemViewCount>` 来存储收到的每条 `ItemViewCount` 消息，保证在发生故障时，状态数据的不丢失和一致性。`ListState` 是 Flink 提供的类似 Java `List` 接口的 State API，它集成了框架的 checkpoint 机制，自动做到了 exactly-once 的语义保证。

 ```
package com.aliyun.market;
 
 
import com.alibaba.fastjson.JSON;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.util.Collector;
 
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
 
 
/**
 * 热门商品topN统计
 * 数据下载 curl https://raw.githubusercontent.com/wuchong/my-flink-project/master/src/main/resources/UserBehavior.csv > UserBehavior.csv
 * 数据在resource 下 UserBehavior.csv
 * 参考： http://wuchong.me/blog/2018/11/07/use-flink-calculate-hot-items/
 * <p>
 * 知识点： aggregate函数 是专门来做统计的，一般跟着keyBy（）后面
 * <p>
 * 官网使用 ： aggregate(SUM, 0).and(MIN, 2)
 */
public class HotTopDemo {
    public static void main(String[] args) throws Exception {
 
        // todo 1，读取kafka数据
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
 
        //todo 获取kafka的配置属性
        args = new String[]{"--input-topic", "user_behavior", "--bootstrap.servers", "node2.hadoop:9091,node3.hadoop:9091",
                "--zookeeper.connect", "node1.hadoop:2181,node2.hadoop:2181,node3.hadoop:2181", "--group.id", "cc2"};
 
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
 
 
//
        Properties pros = parameterTool.getProperties();
//        //todo 指定输入数据为kafka topic
        DataStream<String> kafkaDstream = env.addSource(new FlinkKafkaConsumer010<String>(
                pros.getProperty("input-topic"),
                new SimpleStringSchema(),
                pros).setStartFromLatest()
 
        ).setParallelism(1);
 
 
 
        //todo 我们用 PojoCsvInputFormat 创建输入源。
        DataStream<UserBehavior> dataDstream =kafkaDstream.map(new MapFunction<String, UserBehavior>() {
            @Override
            public UserBehavior map(String input) throws Exception {
                return  JSON.parseObject(input, UserBehavior.class);
            }
        });
 
        //给数据加上了一个水印
        DataStream<UserBehavior> timedData = dataDstream
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<UserBehavior>() {
                    @Override
                    public long extractAscendingTimestamp(UserBehavior userBehavior) {
                        // 原始数据单位秒 ，将其转成毫秒
                        return userBehavior.category_id * 1000;
                    }
                });
 
        //过滤出点击事件
 
        DataStream<UserBehavior> pvData = timedData.filter(new FilterFunction<UserBehavior>() {
            @Override
            public boolean filter(UserBehavior userBehavior) throws Exception {
                return userBehavior.behavior.equals("pv");
            }
        });
 
        //todo 窗口统计点击量,设置窗口大小为1个小时，5分钟滑动一次
        //由于要每隔5分钟统计一次最近一小时每个商品的点击量，所以窗口大小是一小时，每隔5分钟滑动一次。即分别要统计 [09:00, 10:00), [09:05, 10:05), [09:10, 10:10)… 等窗口的商品点击量。是一个常见的滑动窗口需求（Sliding Window）。
 
        DataStream<ItemViewCount> windowedData = pvData.keyBy("item_id") //按字段分组
                .timeWindow(Time.seconds(20), Time.seconds(10))
                .aggregate(new CountAgg(), new WindowResultFunction()); //使用aggregate做增量聚合统计
 
//        TopN 计算最热门商品
        DataStream<String> topItems = windowedData
                .keyBy("windowEnd")
                .process(new TopNHotItems(2));  // 求点击量前3名的商品
        topItems.print();
        env.execute("Hot Items Job");
 
    }
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
    //todo 功能是统计窗口中的条数，即遇到一条数据就加一
 
    public static class CountAgg implements AggregateFunction<UserBehavior, Long, Long> {
 
        @Override
        public Long createAccumulator() { //创建一个数据统计的容器，提供给后续操作使用。
            return 0L;
        }
 
        @Override
        public Long add(UserBehavior userBehavior, Long acc) { //每个元素被添加进窗口的时候调用。
            return acc + 1;
        }
 
        @Override
        public Long getResult(Long acc) {
            ;//窗口统计事件触发时调用来返回出统计的结果。
            return acc;
        }
 
        @Override
        public Long merge(Long acc1, Long acc2) { //只有在当窗口合并的时候调用,合并2个容器
            return acc1 + acc2;
        }
    }
 
    // todo 指定格式输出 ：将每个 key每个窗口聚合后的结果带上其他信息进行输出  进入的数据为Long 返回 ItemViewCount对象
    public static class WindowResultFunction implements WindowFunction<Long, ItemViewCount, Tuple, TimeWindow> {
 
        @Override
        public void apply(
                Tuple key,  // 窗口的主键，即 itemId
                TimeWindow window,  // 窗口
                Iterable<Long> aggregateResult, // 聚合函数的结果，即 count 值
                Collector<ItemViewCount> collector  // 输出类型为 ItemViewCount
        ) throws Exception {
            Long itemId = ((Tuple1<Long>) key).f0;
            Long count = aggregateResult.iterator().next();
            collector.collect(ItemViewCount.of(itemId, window.getEnd(), count));
        }
    }
 
    /**
     * 商品点击量(窗口操作的输出类型)
     */
    public static class ItemViewCount {
        public long itemId;     // 商品ID
        public long windowEnd;  // 窗口结束时间戳
        public long viewCount;  // 商品的点击量
 
        public static ItemViewCount of(long itemId, long windowEnd, long viewCount) {
            ItemViewCount result = new ItemViewCount();
            result.itemId = itemId;
            result.windowEnd = windowEnd;
            result.viewCount = viewCount;
            return result;
        }
    }
 
    /** 求某个窗口中前 N 名的热门点击商品，key 为窗口时间戳，输出为 TopN 的结果字符串 */
    public static class TopNHotItems extends KeyedProcessFunction<Tuple, ItemViewCount, String> {
 
        private final int topSize;
 
        public TopNHotItems(int topSize) {
            this.topSize = topSize;
        }
 
        // 用于存储商品与点击数的状态，待收齐同一个窗口的数据后，再触发 TopN 计算
        private ListState<ItemViewCount> itemState;
 
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 状态的注册
            ListStateDescriptor<ItemViewCount> itemsStateDesc = new ListStateDescriptor<>(
                    "itemState-state",
                    ItemViewCount.class);
            itemState = getRuntimeContext().getListState(itemsStateDesc);
        }
 
        @Override
        public void processElement(
                ItemViewCount input,
                Context context,
                Collector<String> collector) throws Exception {
 
            // 每条数据都保存到状态中
            itemState.add(input);
            // 注册 windowEnd+1 的 EventTime Timer, 当触发时，说明收齐了属于windowEnd窗口的所有商品数据
            context.timerService().registerEventTimeTimer(input.windowEnd + 1);
        }
 
        @Override
        public void onTimer(
                long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            // 获取收到的所有商品点击量
            List<ItemViewCount> allItems = new ArrayList<>();
            for (ItemViewCount item : itemState.get()) {
                allItems.add(item);
            }
            // 提前清除状态中的数据，释放空间
            itemState.clear();
            // 按照点击量从大到小排序
            allItems.sort(new Comparator<ItemViewCount>() {
                @Override
                public int compare(ItemViewCount o1, ItemViewCount o2) {
                    return (int) (o2.viewCount - o1.viewCount);
                }
            });
            // 将排名信息格式化成 String, 便于打印
            StringBuilder result = new StringBuilder();
            result.append("====================================\n");
            result.append("时间: ").append(new Timestamp(timestamp-1)).append("\n");
            for (int i=0;i<topSize;i++) {
                ItemViewCount currentItem = allItems.get(i);
                // No1:  商品ID=12224  浏览量=2413
                result.append("No").append(i).append(":")
                        .append("  商品ID=").append(currentItem.itemId)
                        .append("  浏览量=").append(currentItem.viewCount)
                        .append("\n");
            }
            result.append("====================================\n\n");
            out.collect(result.toString());
        }
    }
}
 ```

```
package com.aliyun.market;

import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.util.Collector;

import java.util.Properties;

/**
 * 商场实时数据统计,基于Flink 1.9版本
 */
public class MarketStreamCount {
    public static void main(String[] args) {


        // todo 1，读取kafka数据
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(8);
        //todo 获取kafka的配置属性
        args = new String[]{"--input-topic", "user_behavior", "--bootstrap.servers", "node2.hadoop:9091,node3.hadoop:9091",
                "--zookeeper.connect", "node1.hadoop:2181,node2.hadoop:2181,node3.hadoop:2181", "--group.id", "cc2"};

        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

         Properties pros = parameterTool.getProperties();
//        //todo 指定输入数据为kafka topic
        DataStream<String> kafkaDstream = env.addSource(new FlinkKafkaConsumer010<String>(
                pros.getProperty("input-topic"),
                new SimpleStringSchema(),
                pros).setStartFromEarliest()

        ).setParallelism(1);

        //todo 2,每层实时顾客人数，实时顾客总数，一天的实时顾客总数

        // 转成json
        DataStream<JSONObject> kafkaDstream2 = kafkaDstream.map(new MapFunction<String, JSONObject>() {
            @Override
            public JSONObject map(String input) throws Exception {
                JSONObject inputJson = null;
                try {
                    inputJson = JSONObject.parseObject(input);
                    return inputJson;
                } catch (Exception e) {
                    e.printStackTrace();
//                    return null;
                }
                return inputJson;
            }
        }).filter(new FilterFunction<JSONObject>() {
            @Override
            public boolean filter(JSONObject jsonObject) throws Exception {
                if (jsonObject!=null){
                    return true;
                }
                return false;
            }
        });
        //给数据加上了一个水印
        DataStream<JSONObject> timedData = kafkaDstream2
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<JSONObject>() {
                    @Override
                    public long extractAscendingTimestamp(JSONObject json) {
                        // 原始数据单位秒 ，将其转成毫秒
                        return json.getLong("category_id") * 1000;
                    }
                });

        //过滤出点击事件
        // 实时客人数，各个层级
        DataStream<JSONObject> windowedData = timedData.keyBy(new KeySelector<JSONObject, String>() {
            @Override
            public String getKey(JSONObject jsonObject) throws Exception {
                return  jsonObject.getString("user_id");
            }
        }).timeWindow(Time.seconds(5L),Time.seconds(1L))
                //使用aggregate做增量聚合统计
                .aggregate(new CountAgg(),new WindowResultFunction());



        windowedData.print();
        try {
            env.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //todo 功能是统计窗口中的条数，即遇到一条数据就加一

    public static class CountAgg implements AggregateFunction<JSONObject, Long, Long> {

        @Override
        public Long createAccumulator() { //创建一个数据统计的容器，提供给后续操作使用。
            return 0L;
        }

        @Override
        public Long add(JSONObject json, Long acc) { //每个元素被添加进窗口的时候调用。
            return acc + 1;
        }

        @Override
        public Long getResult(Long acc) {
            //窗口统计事件触发时调用来返回出统计的结果。
            return acc;
        }

        @Override
        public Long merge(Long acc1, Long acc2) { //只有在当窗口合并的时候调用,合并2个容器
            return acc1 + acc2;
        }
    }

    // todo 指定格式输出
    public static class WindowResultFunction implements WindowFunction<Long, JSONObject, String, TimeWindow> {

        @Override
        public void apply(
                String key,  // 窗口的主键，即 itemId
                TimeWindow window,  // 窗口
                Iterable<Long> aggregateResult, // 聚合函数的结果，即 count 值
                Collector<JSONObject> collector  // 输出类型为 ItemViewCount
        ) throws Exception {

            Long count = aggregateResult.iterator().next();
            //窗口结束时间
            long end = window.getEnd();
            JSONObject json = new JSONObject();
            json.put("key",key);
            json.put("count",count);
            json.put("end",end);
            collector.collect(json);
        }
    }

}
```



#### 打印输出

最后一步我们将结果打印输出到控制台，并调用`env.execute`执行任务。

```
topItems.print();
env.execute("Hot Items Job");
```

### 运行程序

直接运行 main 函数，就能看到不断输出的每个时间点的热门商品ID。





# [flink统计根据账号每30秒 金额的平均值](https://www.cnblogs.com/jiang-it/p/8930897.html)

```
package com.zetyun.streaming.flink;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema;
import org.apache.flink.util.Collector;

import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

/**
 * Created by jyt on 2018/4/10.
 * 基于账号计算每30秒 金额的平均值
 */
public class EventTimeAverage {

    public static void main(String[] args) throws Exception {
        final ParameterTool parameterTool = ParameterTool.fromArgs(args);
        String topic = parameterTool.get("topic", "accountId-avg");
        Properties properties = parameterTool.getProperties();
        properties.setProperty("bootstrap.servers", "192.168.44.101:9092");
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        ObjectMapper objectMapper = new ObjectMapper();
        SingleOutputStreamOperator<ObjectNode> source = env.addSource(new FlinkKafkaConsumer010(
                topic,
                new JSONDeserializationSchema(),
                properties));
        //设置WaterMarks方式一
        /*SingleOutputStreamOperator<ObjectNode> objectNodeOperator = source.assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<ObjectNode>(Time.seconds(15)) {
            @Override
            public long extractTimestamp(ObjectNode element) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                Date eventTime = null;
                try {
                    eventTime = format.parse(element.get("eventTime").asText());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return eventTime.getTime();
            }
        });*/
        //设置WaterMarks方式二
        SingleOutputStreamOperator<ObjectNode> objectNodeOperator = source.assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarks<ObjectNode>() {
            public long currentMaxTimestamp = 0L;
            public static final long maxOutOfOrderness = 10000L;//最大允许的乱序时间是10s
            Watermark a = null;
            SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");


            @Nullable
            @Override
            public Watermark getCurrentWatermark() {
                a = new Watermark(currentMaxTimestamp - maxOutOfOrderness);
                return a;
            }

            @Override
            public long extractTimestamp(ObjectNode jsonNodes, long l) {
                String time = jsonNodes.get("eventTime").asText();
                long timestamp = 0;
                try {
                    timestamp = format.parse(time).getTime();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp);
                return timestamp;
            }
        });
        KeyedStream<Tuple3<String, Double, String>, Tuple> keyBy = objectNodeOperator.map(new MapFunction<ObjectNode, Tuple3<String, Double, String>>() {
            @Override
            public Tuple3<String, Double, String> map(ObjectNode jsonNodes) throws Exception {
                System.out.println(jsonNodes.get("accountId").asText() + "==map====" + jsonNodes.get("amount").asDouble() + "===map===" + jsonNodes.get("eventTime").asText());
                return new Tuple3<String, Double, String>(jsonNodes.get("accountId").asText(), jsonNodes.get("amount").asDouble(), jsonNodes.get("eventTime").asText());
            }
        }).keyBy(0);


        SingleOutputStreamOperator<Object> apply = keyBy.window(TumblingEventTimeWindows.of(Time.seconds(30))).apply(new WindowFunction<Tuple3<String,Double,String>, Object, Tuple, TimeWindow>() {
            @Override
            public void apply(Tuple tuple, TimeWindow timeWindow, Iterable<Tuple3<String, Double, String>> iterable, Collector<Object> collector) throws Exception {
                Iterator<Tuple3<String, Double, String>> iterator = iterable.iterator();
                int count =0;
                double num = 0.0;
                ///Tuple2<String, Double> result = null;
                Tuple3<String, Double, String> next = null;
                String accountId = null ;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    System.out.println(next);
                    accountId=next.f0;
                    num += next.f1;
                    count++;
                }
                if (next != null) {

                    collector.collect(new Tuple2<String, Double>(accountId,num/count));
                }
            }
        });


        apply.print();
        //apply.addSink(new FlinkKafkaProducer010<String>("192.168.44.101:9092","wiki-result",new SimpleStringSchema()));
        env.execute("AverageDemo");
    }

}
```

