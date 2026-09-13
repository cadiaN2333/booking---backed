package com.example.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.booking.domain.entity.User;
import com.example.booking.mapper.UserMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.SlotService;
import java.util.List;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DataInitializerTest {

  @Test
  void 管理员密码配置读取BOOKING_ADMIN_PASSWORD并提供本地默认值() throws Exception {
    Field field = DataInitializer.class.getDeclaredField("adminPassword");

    assertThat(field.getAnnotation(Value.class).value())
        .isEqualTo("${BOOKING_ADMIN_PASSWORD:Admin@123456}");
  }

  @Test
  void 初始化时注入自定义管理员密码并传给PasswordEncoder() {
    UserMapper userMapper = mock(UserMapper.class);
    VenueMapper venueMapper = mock(VenueMapper.class);
    SlotService slotService = mock(SlotService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
    when(venueMapper.selectList(any())).thenReturn(List.of());
    String customAdminPassword = "EnvSecret@2026";
    when(passwordEncoder.encode(customAdminPassword)).thenReturn("encoded-admin");
    when(passwordEncoder.encode("123456")).thenReturn("encoded-demo");

    DataInitializer initializer =
        new DataInitializer(
            userMapper, venueMapper, slotService, passwordEncoder);
    ReflectionTestUtils.setField(initializer, "adminPassword", customAdminPassword);
    initializer.run(new DefaultApplicationArguments());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
    User admin =
        captor.getAllValues().stream()
            .filter(user -> "admin".equals(user.getUsername()))
            .findFirst()
            .orElseThrow();
    assertThat(admin.getRole()).isEqualTo(2);
    assertThat(admin.getPassword()).isEqualTo("encoded-admin");
    verify(passwordEncoder).encode(customAdminPassword);
  }

  @Test
  void 初始化每次调用全量时段生成以补齐部分已有数据() {
    UserMapper userMapper = mock(UserMapper.class);
    VenueMapper venueMapper = mock(VenueMapper.class);
    SlotService slotService = mock(SlotService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
    when(venueMapper.selectList(any())).thenReturn(List.of());

    DataInitializer initializer =
        new DataInitializer(userMapper, venueMapper, slotService, passwordEncoder);
    ReflectionTestUtils.setField(initializer, "generateDays", 7);

    initializer.run(new DefaultApplicationArguments());

    verify(slotService).generateAll(7);
  }
}
