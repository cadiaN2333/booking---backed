package com.example.booking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.domain.entity.User;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.enums.UserRoleEnum;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.mapper.UserMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.SlotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 首次启动准备演示数据：两个账号 + 把所有场馆挂到演示商家名下 + 铺时段 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

  /** 演示账号密码。仅用于本地演示，真实环境不要预置账号 */
  private static final String DEMO_PASSWORD = "123456";

  private final UserMapper userMapper;
  private final VenueMapper venueMapper;
  private final SlotMapper slotMapper;
  private final SlotService slotService;
  private final PasswordEncoder passwordEncoder;

  @Value("${booking.generate-days:14}")
  private int generateDays;

  @Override
  public void run(ApplicationArguments args) {
    User merchant = ensureUser("merchant", "星辰体育（演示商家）", UserRoleEnum.MERCHANT.getCode());
    ensureUser("customer", "体验用户", UserRoleEnum.CUSTOMER.getCode());
    bindVenuesToMerchant(merchant.getId());

    if (slotMapper.selectCount(null) == 0) {
      int n = slotService.generateAll(generateDays);
      log.info("首次启动，已生成 {} 个时段", n);
    }
  }

  private User ensureUser(String username, String nickname, int role) {
    User exists =
        userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    if (exists != null) {
      return exists;
    }
    User u = new User();
    u.setUsername(username);
    u.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
    u.setNickname(nickname);
    u.setRole(role);
    u.setStatus(1);
    userMapper.insert(u);
    log.info("已创建演示账号 {} / {} (role={})", username, DEMO_PASSWORD, role);
    return u;
  }

  private void bindVenuesToMerchant(Long merchantId) {
    List<Venue> unbound =
        venueMapper.selectList(new LambdaQueryWrapper<Venue>().isNull(Venue::getMerchantId));
    for (Venue v : unbound) {
      v.setMerchantId(merchantId);
      venueMapper.updateById(v);
    }
    if (!unbound.isEmpty()) {
      log.info("已把 {} 个场馆挂到演示商家 id={} 名下", unbound.size(), merchantId);
    }
  }
}
