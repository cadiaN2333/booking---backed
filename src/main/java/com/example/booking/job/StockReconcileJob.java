package com.example.booking.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.domain.entity.Slot;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.service.SlotCacheService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期用 DB 校准 Redis 库存计数。只处理近几天的数据，避免全表扫描 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconcileJob {

    private final SlotMapper slotMapper;
    private final SlotCacheService slotCacheService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void reconcile() {
        LocalDate today = LocalDate.now();
        List<Slot> slots =
                slotMapper.selectList(
                        new LambdaQueryWrapper<Slot>()
                                .ge(Slot::getBizDate, today)
                                .le(Slot::getBizDate, today.plusDays(3)));

        int fixed = 0;
        for (Slot s : slots) {
            String cached = slotCacheService.getStock(s.getId());
            if (cached == null) {
                continue; // 未预热，跳过，等首次查询时自然种入
            }
            if (!cached.equals(String.valueOf(s.getAvailable()))) {
                slotCacheService.alignStock(s.getId(), s.getAvailable());
                fixed++;
                log.warn("库存计数不一致，已按 DB 修正 slotId={} redis={} db={}",
                        s.getId(), cached, s.getAvailable());
            }
        }
        if (fixed > 0) {
            log.warn("本轮对账修正 {} 条，说明有路径没对齐，去查原因", fixed);
        }
    }
}