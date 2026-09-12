package com.example.booking.service.impl;

import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.SlotCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 时段缓存实现。设计理由见接口 javadoc */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotCacheServiceImpl implements SlotCacheService {

    private static final String LIST_KEY = "slot:list:%d:%s";
    private static final String LOCK_KEY = "slot:rebuild:%d:%s";
    private static final String STOCK_KEY = "slot:stock:%d";

    private static final Duration PHYSICAL_TTL = Duration.ofMinutes(30);
    private static final Duration LOGICAL_TTL = Duration.ofMinutes(5);
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration STOCK_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;


    @Override
    public String stockKey(Long slotId) {
        return String.format(STOCK_KEY, slotId);
    }


    private String listKey(Long courtId, String date) {
        return String.format(LIST_KEY, courtId, date);
    }

    private String lockKey(Long courtId, String date) {
        return String.format(LOCK_KEY, courtId, date);
    }

    /* ---------------- 日历缓存 ---------------- */

    /** 返回原始 JSON；调用方需要自己判断是不是空值标记 */
    @Override
    public String getRawDay(Long courtId, String date) {
        return redis.opsForValue().get(listKey(courtId, date));
    }

    @Override
    public CachedDay parse(String json) {
        try {
            return objectMapper.readValue(json, CachedDay.class);
        } catch (Exception e) {
            log.warn("缓存反序列化失败", e);
            return null;
        }
    }

    @Override
    public void putDay(Long courtId, String date, List<SlotVO> slots) {
        try{
            String json = objectMapper.writeValueAsString(
                    new CachedDay(System.currentTimeMillis() + LOGICAL_TTL.toMillis(), slots));
            // 随机抖动：避免大量 key 同一秒集体失效造成雪崩
            long ttl = PHYSICAL_TTL.getSeconds() + ThreadLocalRandom.current().nextInt(300);
            redis.opsForValue().set(listKey(courtId, date), json, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("写日历缓存失败 courtId={} date={}", courtId, date, e);
        }
    }

    @Override
    public void putNull(Long courtId, String date) {
        redis.opsForValue().set(listKey(courtId, date), NULL_MARK, NULL_TTL);
    }

    /** 写操作后删除缓存（Cache Aside 的"删"这一步） */
    @Override
    public void evictDay(Long courtId, String date) {
        redis.delete(listKey(courtId, date));
    }

    /* ---------------- 重建互斥锁 ---------------- */

    /** SETNX：抢到返回 true */
    @Override
    public boolean tryLockRebuild(Long courtId, String date) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(lockKey(courtId, date), "1", LOCK_TTL));
    }

    @Override
    public void unlockRebuild(Long courtId, String date) {
        redis.delete(lockKey(courtId, date));
    }

    /* ---------------- 库存计数 ---------------- */

    /** SETNX 预热：只种一次，不覆盖正在被扣减的计数 */
    @Override
    public void warmUpStock(Long slotId, int available) {
        redis.opsForValue().setIfAbsent(
                stockKey(slotId), String.valueOf(available), STOCK_TTL);
    }

    @Override
    public String getStock(Long slotId) {
        return redis.opsForValue().get(stockKey(slotId));
    }

    /** 返回值约定：OK=预扣成功，EMPTY=库存不足，UNREADY=未预热需回源 */
    private static final DefaultRedisScript<String> DEDUCT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local v = redis.call('GET', KEYS[1])
                    if not v then return 'UNREADY' end
                    if tonumber(v) < 1 then return 'EMPTY' end
                    redis.call('DECRBY', KEYS[1], 1)
                    return 'OK'
                    """,
                    String.class);

    @Override
    public String tryDeduct(Long slotId) {
        String r = redis.execute(DEDUCT_SCRIPT, List.of(stockKey(slotId)));
        return r == null ? "UNREADY" : r;
    }

    /** 回补：DB 扣减失败时把预扣的还回去 */
    @Override
    public void giveBack(Long slotId) {
        redis.opsForValue().increment(stockKey(slotId));
    }

    /** 以 DB 为准绝对对齐计数。注意是 SET 不是 INCRBY，见第 8 步的说明 */
    @Override
    public void alignStock(Long slotId, int available) {
        redis.opsForValue().set(stockKey(slotId), String.valueOf(available), STOCK_TTL);
    }
}
