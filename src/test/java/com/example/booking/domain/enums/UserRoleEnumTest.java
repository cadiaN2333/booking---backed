package com.example.booking.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserRoleEnumTest {

  @Test
  void 角色2是管理员() {
    assertThat(UserRoleEnum.values())
        .anySatisfy(role -> {
          assertThat(role.getCode()).isEqualTo(2);
          assertThat(role.name()).isEqualTo("ADMIN");
        });
    assertThat(UserRoleEnum.isAdmin(2)).isTrue();
  }
}
