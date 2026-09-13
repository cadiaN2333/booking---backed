package com.example.booking.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserRoleEnumTest {

  @Test
  void 三种角色编码固定为顾客0商家1管理员2() {
    assertThat(UserRoleEnum.CUSTOMER.getCode()).isEqualTo(0);
    assertThat(UserRoleEnum.MERCHANT.getCode()).isEqualTo(1);
    assertThat(UserRoleEnum.ADMIN.getCode()).isEqualTo(2);
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
