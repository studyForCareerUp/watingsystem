package com.example.flow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.Instant;

import static com.example.flow.exception.ErrorCode.QUEUE_ALREADY_REGISTERED_USER;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserQueueService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private final String USER_QUEUE_WAIT_KEY = "users:queue:%s:wait";
    private final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "users:queue:*:wait";
    private final String USER_QUEUE_PROCEED_KEY = "users:queue:%s:proceed";

    @Value("${scheduler.enabled}")
    private Boolean scheduling = false;

    //대기열 등록 API
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        // redis sortedset
        // - key: userId
        // - value: unix timestamp
        // zscan user-queue 0 명령어를 통해 redis에서 user-queue에 등록된 유저들 직접 확인 가능
        // zcard user-queue 명령어를 통해 몇명의 유저가 등록되어 있는지 확인 가능
        long unixTimestamp = Instant.now().getEpochSecond();
        return reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
                .filter(i -> i)
                .switchIfEmpty(Mono.error(QUEUE_ALREADY_REGISTERED_USER.build())) // 이미 등록되어 있는 유저의 경우 에러 반환
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString()))
                .log()
                .map(i -> i >= 0 ? i + 1 : i); //rank 는 0부터 시작하므로 보정
    }


    //진입을 허용
    public Mono<Long> allowUser(final String queue, final Long count) {
        //진입 허용하는 순서
        // 1. wait queue 사용자를 제거
        // 2. proceed queue 사용자를 추가
        // scan 0
        // zscan 은 sortedset 의 약자
        // zrem은 인자 제거
        // zrem key value
        // ex) zrem user:queue:default:proceed 608
        // zscan users:queue:default:wait 0
        // zscan users:queue:default:proceed 0
        return reactiveRedisTemplate.opsForZSet()
                .popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count) //score 가 제일 낮은 순서부터 pop 됨
                .flatMap(member -> reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond()))
                .count();
    }

    //진입이 가능한 상태인지 조회
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0);

    }

    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 3000) // 초기 앱 기동 후 initialDelay 초 뒤부터 시작, fixedDelay 초마다 메서드 호출
    private void scheduleAllowUser() {
        if (!scheduling) {
            log.info("passed scheduling...");
            return;
        }

        log.info("called scheduling...");

        long maxAllowUserCount = 300L; //몇명씩 진행열로 넣을 지

        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                        .count(100) //한번에 몇개의 종류의 대기열 셋을 가져올 지
                        .build())
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(allowed, queue)))
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT1(), tuple.getT2())))
                .subscribe();

    }
}
