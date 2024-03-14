package com.example.flow.service;

import com.example.flow.EmbeddedRedis;
import com.example.flow.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@Import(EmbeddedRedis.class)
@ActiveProfiles("test")
class UserQueueServiceTest {

    @Autowired
    private UserQueueService userQueueService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;


    @BeforeEach
    public void beforeEach() {
        // 테스트가 끝나고 레디스를 초기화 해주기 위해서
        System.out.println("UserQueueServiceTest.beforeEach");
        ReactiveRedisConnection reactiveConnection = reactiveRedisTemplate.getConnectionFactory().getReactiveConnection();
        reactiveConnection.serverCommands().flushAll().subscribe();
    }

    @Test
    @DisplayName("대기열에 유저를 등록한다.")
    void registerWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue("default", 2L))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue("default", 3L))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("중복되는 유저가 등록되는 경우 예외가 발생한다.")
    void alreadyRegisteredWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L))
                .expectError(ApplicationException.class)
                .verify();

    }

    @Test
    @DisplayName("대기열이 비었으면 진행열에 옮길 유저가 없다.")
    void emptyAllowedUser() {
        StepVerifier.create(userQueueService.allowUser("default", 5L))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에 등록한 수만큼 유저를 진행열로 옮긴다.")
    void allowUser() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L)
                        .then(userQueueService.registerWaitQueue("default", 2L))
                        .then(userQueueService.registerWaitQueue("default", 3L))
                        .then(userQueueService.allowUser("default", 3L)))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에 등록한 수보다 더 많은 유저를 진행열로 옮긴다.")
    void allowUserGraterThenCount() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L)
                        .then(userQueueService.registerWaitQueue("default", 2L))
                        .then(userQueueService.registerWaitQueue("default", 3L))
                        .then(userQueueService.allowUser("default", 5L)))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에만 등록한 유저는 통과 권한이 없다.")
    void isNotAllowedWhenRegisterWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L)
                        .then(userQueueService.isAllowed("default", 1L)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에 등록이 안된 유저를 진행열로 옮기면 그 유저는 통과 권한이 없다.")
    void isNotAllowedWhenAllowDifferentUser() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L)
                        .then(userQueueService.allowUser("default", 1L))
                        .then(userQueueService.isAllowed("default", 2L)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에 등록한 유저를 진행열로 옮기고, 그 유저가 통과 권한이 있는지 확인한다.")
    void isAllowed() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 1L)
                        .then(userQueueService.allowUser("default", 1L))
                        .then(userQueueService.isAllowed("default", 1L)))
                .expectNext(true)
                .verifyComplete();


    }

    @Test
    @DisplayName("대기열에 있는 유저의 순위를 반환한다.")
    void getRank() {
        StepVerifier.create(userQueueService.registerWaitQueue("default", 100L)
                        .then(userQueueService.getRank("default", 100L)))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue("default", 101L)
                        .then(userQueueService.getRank("default", 101L)))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    @DisplayName("빈대기열에 있는 유저의 순위는 없다.")
    void emptyRank() {
        StepVerifier.create(userQueueService.getRank("default", 101L))
                .expectNext(-1L)
                .verifyComplete();
    }
}