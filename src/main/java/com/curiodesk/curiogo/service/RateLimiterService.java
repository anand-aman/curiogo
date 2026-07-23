package com.curiodesk.curiogo.service;


import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class RateLimiterService implements DisposableBean{

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class.getName());

    private final Supplier<BucketConfiguration> bucketConfig;

    private final ProxyManager<String> proxyManager;
    private final StatefulRedisConnection<String, byte[]> connection;

    private final Counter allowed;
    private final Counter blocked;
    private final Counter errors;

    public RateLimiterService(RedisClient rateLimitRedisClient,
                              MeterRegistry meterRegistry,
                              @Value("${app.rate-limit.enabled:true}") boolean enabled,
                              @Value("${app.rate-limit.capacity:10}") long capacity,
                              @Value("${app.rate-limit.refill-tokens:10}") long refillTokens,
                              @Value("${app.rate-limit.refill-period:1m}") Duration refillPeriod) {

        this.bucketConfig = () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .build();

        this.allowed = Counter.builder("curiogo.rate_limit.decisions")
                .description("Link-create rate-limit decisions")
                .tag("result", "allowed")
                .register(meterRegistry);

        this.blocked = Counter.builder("curiogo.rate_limit.decisions")
                .description("Link-create rate-limit decisions")
                .tag("result", "blocked")
                .register(meterRegistry);

        this.errors = Counter.builder("curiogo.rate_limit.decisions")
                .description("Link-create rate-limit decisions")
                .tag("result", "error")
                .register(meterRegistry);

        if(!enabled) {
            this.connection = null;
            this.proxyManager = null;
            log.info("Rate Limiter disabled via app.rate-limit.enabled=false - creates are unlimited");
            return;
        }

        StatefulRedisConnection<String, byte[]> conn = null;
        ProxyManager<String> pm = null;

        try {
            conn = rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
            pm = LettuceBasedProxyManager.builderFor(conn)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(refillPeriod))
                    .build();
            log.info("Rate Limiter active : {} tokens, refill {} per {} per client IP", capacity, refillTokens, refillPeriod);
        } catch (Exception e) {
            log.warn("Rate limiter Redis unavailable at startup - failing OPEN (creates unlimited) corrId={}: {}", UUID.randomUUID(), e.getMessage());
        }
        this.connection = conn;
        this.proxyManager = pm;
    }

    public boolean tryAcquire(String clientId) {
        if(proxyManager == null) {
            allowed.increment();
            return true;
        }
        try {
            Bucket bucket = proxyManager.builder().build(clientId, bucketConfig);
            boolean ok = bucket.tryConsume(1);
            (ok ? allowed : blocked).increment();
            return ok;
        } catch (Exception e){
            errors.increment();
            log.warn("Rate limiter check failed - failing OPEN clientId={} corrId={}: {}",
                    clientId, UUID.randomUUID(), e.getMessage());
            return true;
        }
    }

    @Override
    public void destroy() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
