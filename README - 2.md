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

