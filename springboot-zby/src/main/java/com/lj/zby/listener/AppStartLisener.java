package com.lj.zby.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class AppStartLisener implements ApplicationListener {
    private Logger logger = LoggerFactory.getLogger(AppStartLisener.class);
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartedEvent){
            logger.info("Application Start ....");
//            System.out.println("Application Start ....");
        }else if (event instanceof ApplicationFailedEvent){
            logger.info("Application Start Failed case:"+((ApplicationFailedEvent) event).getException());
//            System.out.println("Application Start Failed case:"+((ApplicationFailedEvent) event).getException());
        }else if (event instanceof ApplicationReadyEvent){
            logger.info("Application Start successfully");
//            System.out.println("Application Start successfully");
        }
    }
}
