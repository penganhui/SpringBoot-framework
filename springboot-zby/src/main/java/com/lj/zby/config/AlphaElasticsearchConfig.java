package com.lj.zby.config;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;


@Component
@Configuration
@EnableElasticsearchRepositories(elasticsearchTemplateRef = "XXTemplate")
public class AlphaElasticsearchConfig {
    @Value("spring.data.elasticsearch.cluster-name")
    private String clusterName;

    @Value("spring.data.elasticsearch.cluster-nodes")
    private String clusterNodes;

    @Value("spring.data.elasticsearch.cluster-nodes")
    private String clusterPort;

    @Bean
    public Transport alphaClient() throws Exception{
        Settings settings = Settings.builder().put(clusterName,clusterNodes)
                .put("client.transport.sniff",false).build();
        return (Transport) new PreBuiltTransportClient(settings).addTransportAddresses(getTransportAddresses());
    }

    private TransportAddress[] getTransportAddresses() throws UnknownHostException {
        List<TransportAddress> list = new ArrayList<>();
        if (StringUtils.isEmpty(clusterNodes)){
            String[] urlList = clusterNodes.split(",");
            for (String str: urlList  ) {
                TransportAddress transportAddress = new TransportAddress(InetAddress.getByName(str),clusterPort);
                list.add(transportAddress);
            }
            return list.toArray(new TransportAddress[list.size()]);
        }
    }

    @Bean(name = "XXTemplate")
    public ElasticsearchTemplate alphaTemplate() throws Exception{
        return new ElasticsearchTemplate((Client) alphaClient());
    }

}
