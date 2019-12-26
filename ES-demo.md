```
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>org.elasticsearch.springboot</groupId>
    <artifactId>spring-boot-starter-elasticsearch</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>spring-boot-starter-elasticsearch</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>1.8</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        
        <elasticsearch.version>5.4.3</elasticsearch.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <version>1.5.2.RELEASE</version>
        </dependency>
        <dependency>
            <groupId>org.elasticsearch</groupId>
            <artifactId>elasticsearch</artifactId>
            <version>${elasticsearch.version}</version>
        </dependency>
        <dependency>
            <groupId>org.elasticsearch.client</groupId>
            <artifactId>transport</artifactId>
            <version>${elasticsearch.version}</version>
        </dependency>
        <dependency>
            <groupId>org.elasticsearch.client</groupId>
            <artifactId>x-pack-transport</artifactId>
            <version>${elasticsearch.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-api</artifactId>
            <version>2.7</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
            <version>2.7</version>
        </dependency>
    </dependencies>

</project>
```

```java
package org.elasticsearch.springboot;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.data.elasticsearch")
public class ElasticSearchProperties {
    private String clusterName;
    private String clusterNodes;
    private Map<String, String> properties = new HashMap<String, String>();

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getClusterNodes() {
        return clusterNodes;
    }

    public void setClusterNodes(String clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

}
```

```java
package org.elasticsearch.springboot;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.elasticsearch.xpack.client.PreBuiltXPackTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchAutoConfiguration implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchAutoConfiguration.class);

    @Autowired
    private ElasticSearchProperties properties;

    private Releasable releasable;

    @Bean
    public TransportClient transportClient() throws UnknownHostException {
        if (isNotBlank(properties.getClusterName()) && isNotBlank(properties.getClusterNodes())) {
            Builder builder = Settings.builder();
            builder.put("cluster.name", properties.getClusterName());
            // 创建client
            String[] arr = properties.getClusterNodes().split(":");
            String host = arr[0];
            Integer port = Integer.valueOf(arr[1]);
            TransportClient client = null;
            if (properties.getXpack() != null && isNotBlank(properties.getXpack().getUser()) && isNotBlank(properties.getXpack().getPassword())) {
                builder.put("xpack.security.user", properties.getXpack().getUser() + ":" + properties.getXpack().getPassword());
                client = new PreBuiltXPackTransportClient(builder.build()).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
            } else {
                client = new PreBuiltTransportClient(builder.build()).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
            }
            releasable = client;
            return client;
        }
        return null;
    }

    private boolean isNotBlank(String clusterName) {
        return clusterName != null && clusterName.length() > 0;
    }

    @Override
    public void destroy() throws Exception {
        if (this.releasable != null) {
            try {
                if (logger.isInfoEnabled()) {
                    logger.info("Closing Elasticsearch client");
                }
                try {
                    this.releasable.close();
                } catch (NoSuchMethodError ex) {
                    // Earlier versions of Elasticsearch had a different method
                    // name
                    ReflectionUtils.invokeMethod(ReflectionUtils.findMethod(Releasable.class, "release"), this.releasable);
                }
            } catch (final Exception ex) {
                if (logger.isErrorEnabled()) {
                    logger.error("Error closing Elasticsearch client: ", ex);
                }
            }
        }
    }
}
```

```
spring.data.elasticsearch.cluster-name=CollectorDBCluster
spring.data.elasticsearch.cluster-nodes=192.168.1.63:9300
spring.data.elasticsearch.xpack.user=elastic
spring.data.elasticsearch.xpack.password=changeme
```

