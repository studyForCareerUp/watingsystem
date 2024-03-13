package com.example.flow;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

import java.io.IOException;

@TestConfiguration
public class EmbeddedRedis {
    private final RedisServer redisServer;

    public EmbeddedRedis() throws IOException {
        this.redisServer = new RedisServer(63790);
    }

    @PostConstruct
    public void start() throws IOException {
        System.out.println("EmbeddedRedis.start");
        this.redisServer.start();

    }

    @PreDestroy
    public void stop() throws IOException {
        System.out.println("EmbeddedRedis.stop");
        this.redisServer.stop();

    }
}
