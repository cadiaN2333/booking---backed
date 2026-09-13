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

  @Test
  void 空值和非法角色都不是受支持角色() {
    assertThat(UserRoleEnum.isAdmin(null)).isFalse();
    assertThat(UserRoleEnum.isMerchant(null)).isFalse();
    assertThat(UserRoleEnum.isAdmin(-1)).isFalse();
    assertThat(UserRoleEnum.isMerchant(3)).isFalse();
  }
}
