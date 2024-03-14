package com.example.flow.controller;

import com.example.flow.dto.AllowUserResponse;
import com.example.flow.dto.AllowedUserResponse;
import com.example.flow.dto.RankNumberResponse;
import com.example.flow.dto.RegisterUserResponse;
import com.example.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
@Slf4j
public class UserQueueController {

    private final UserQueueService userQueueService;

    /**
     * @param queue  대기열을 다수로 운용할 수 있도록 queue 정보 추가
     * @param userId 어떤 사용자가 등록될건지
     * @return
     */
    @PostMapping
    public Mono<RegisterUserResponse> registerUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                   @RequestParam(name = "user_id") Long userId) {
        return userQueueService.registerWaitQueue(queue, userId)
                .map(RegisterUserResponse::new);
    }

    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                             @RequestParam(name = "count") Long count) {
        return userQueueService.allowUser(queue, count)
                .map(allowed -> new AllowUserResponse(count, allowed));

    }

    @GetMapping("/allowed")
    public Mono<AllowedUserResponse> isAllowed(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                               @RequestParam(name = "user_id") Long userId,
                                                @RequestParam(name = "token") String token) {

//        return userQueueService.isAllowed(queue, userId)
//                .map(AllowedUserResponse::new);

        return userQueueService.isAllowedByToken(queue, userId, token)
                .map(AllowedUserResponse::new);
    }

    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                @RequestParam(name = "user_id") Long user_id) {
        return userQueueService.getRank(queue, user_id)
                .map(RankNumberResponse::new);
    }

    @GetMapping("/touch")
    public Mono<?> touch(@RequestParam(name = "queue", defaultValue = "default") String queue,
                         @RequestParam(name = "user_id") Long userId,
                         ServerWebExchange exchange) {

        return Mono.defer(() -> userQueueService.generateToken(queue, userId))
                .map(token -> {
                    exchange.getResponse().addCookie(
                            ResponseCookie
                                    .from("user-queue-%s-token".formatted(queue), token)
                                    .maxAge(Duration.ofSeconds(300))
                                    .path("/")
                                    .build()
                    );

                    return token;
                });

    }

}
