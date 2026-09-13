package com.example.booking.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditResourceDefaultsTest {

  @Test
  void 新场馆默认待审核且下架() {
    Venue venue = new Venue();

    assertThat(venue.getAuditStatus()).isZero();
    assertThat(venue.getStatus()).isZero();
  }

  @Test
  void 新场地默认待审核且下架() {
    Court court = new Court();

    assertThat(court.getAuditStatus()).isZero();
    assertThat(court.getStatus()).isZero();
  }
}
