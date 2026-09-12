package com.example.booking.service;

import com.example.booking.domain.vo.SlotVO;

import java.util.List;

/**
 * 时段缓存。实现见 impl/SlotCacheServiceImpl
 *
 * <p>用 StringRedisTemplate 而不是默认的 RedisTemplate：
 * 后者默认 JDK 序列化，redis-cli 里是二进制乱码；前者存 JSON 字符串，可以直接肉眼看。
 *
 * <p>防击穿用「逻辑过期」：value 里带一个 expireTs，物理 TTL 设置得比它长。
 * 这样 key 不会真的消失，读到时发现逻辑过期就异步重建，避免热点 key 失效瞬间打穿 DB。
 */
public interface SlotCacheService {

    /** 逻辑过期包装体 */
    record CachedDay(long expireTs, List<SlotVO> slots) {}

    /** 空值标记：查不到数据时缓存它，防止同一个不存在的 key 反复打 DB */
    String NULL_MARK = "__NULL__";

    String stockKey(Long slotId);

    /** 返回原始 JSON；调用方需要自己判断是不是空值标记 */
    String getRawDay(Long courtId, String date);

    CachedDay parse(String json);

    void putDay(Long courtId, String date, List<SlotVO> slots);

    void putNull(Long courtId, String date);

    /** 写操作后删除缓存（Cache Aside 的"删"这一步） */
    void evictDay(Long courtId, String date);

    /** SETNX 抢重建锁，抢到返回 true */
    boolean tryLockRebuild(Long courtId, String date);

    void unlockRebuild(Long courtId, String date);

    /** SETNX 预热：只种一次，不覆盖正在被扣减的计数 */
    void warmUpStock(Long slotId, int available);

    String getStock(Long slotId);

    // 第 7 步会在这里补三个方法：tryDeduct / giveBack / alignStock
    /** 返回 OK=预扣成功 / EMPTY=库存不足 / UNREADY=未预热需回源 */
    String tryDeduct(Long slotId);

    /** 回补：DB 扣减失败时把预扣的还回去 */
    void giveBack(Long slotId);

    /** 以 DB 为准绝对对齐计数 */
    void alignStock(Long slotId, int available);
}
