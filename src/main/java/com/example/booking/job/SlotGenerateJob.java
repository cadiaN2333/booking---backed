package com.example.booking.job;

import com.example.booking.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨滚动生成未来 N 天时段。真实项目里换成 XXL-Job 分片广播 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotGenerateJob {

  private final SlotService slotService;

  @Value("${booking.generate-days:14}")
  private int generateDays;

  @Scheduled(cron = "0 30 2 * * ?")
  public void generateDaily() {
    int n = slotService.generateAll(generateDays);
    log.info("定时生成时段完成，共 {} 个", n);
  }
}
