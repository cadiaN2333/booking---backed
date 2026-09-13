package com.example.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.booking.domain.entity.User;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.mapper.UserMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.SlotService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DataInitializerTest {

  @Test
  void 初始化时创建管理员并使用本地演示默认密码() {
    UserMapper userMapper = mock(UserMapper.class);
    VenueMapper venueMapper = mock(VenueMapper.class);
    SlotMapper slotMapper = mock(SlotMapper.class);
    SlotService slotService = mock(SlotService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
    when(venueMapper.selectList(any())).thenReturn(List.of());
    when(slotMapper.selectCount(null)).thenReturn(1L);
    when(passwordEncoder.encode("Admin@123456")).thenReturn("encoded-admin");
    when(passwordEncoder.encode("123456")).thenReturn("encoded-demo");

    DataInitializer initializer =
        new DataInitializer(
            userMapper, venueMapper, slotMapper, slotService, passwordEncoder);
    ReflectionTestUtils.setField(initializer, "adminPassword", "Admin@123456");
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
  }
}
