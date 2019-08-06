package com.lj.zby.Interceptor;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 *
 * 任务处理器，监听kafka队列中的消息，消费并处理
 *
 * @author Logan
 * @version 1.0.0
 * @createDate 2019-05-07
 *
 */
//@Component
public class MessageHandler {

    @KafkaListener(topics = { "test-topic" })
    public void handle(String message) {
        System.out.println("[ 处理器开始处理消息 ]" + System.currentTimeMillis());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(message);

        System.out.println("[ 处理器处理消息完成 ]" + System.currentTimeMillis());
    }

    @KafkaListener(topics = { "test-topic" })
    public void handle(ConsumerRecord<String, String> record) {
        System.out.println("[ 处理器开始处理消息 ]" + System.currentTimeMillis());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(record);

        System.out.println("[ 处理器处理消息完成 ]" + System.currentTimeMillis());
    }

}
