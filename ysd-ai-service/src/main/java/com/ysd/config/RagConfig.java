package com.ysd.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RagConfig {

    @Bean(destroyMethod = "close")
    public JedisPooled ragJedisPooled(@Value("${spring.data.redis.host:127.0.0.1}") String host,
                                      @Value("${spring.data.redis.port:6379}") int port,
                                      @Value("${spring.data.redis.database:0}") int database) {
        // 连接池的每条连接都要在建连时切库；对 JedisPooled 调 select() 只影响单条连接
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .database(database)
                .build();
        return new JedisPooled(new HostAndPort(host, port), config);
    }

    @Bean
    public RedisVectorStore ragVectorStore(JedisPooled ragJedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(ragJedisPooled, embeddingModel)
                .indexName("ysd_rag_idx")
                .prefix("ysd:rag:")
                .initializeSchema(true)
                .build();
    }
}
