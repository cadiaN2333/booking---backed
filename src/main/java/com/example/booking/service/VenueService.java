package com.example.booking.service;

import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.dto.MerchantVenueRequest;
import java.util.List;

public interface VenueService {

  /** 顾客端：全部上架场馆 */
  List<Venue> listOnline();

  /** 商家端：当前登录商家名下的场馆 */
  List<Venue> listMine();

  /** 商家端：创建本人场馆，资源默认待审核且下架。 */
  Venue create(MerchantVenueRequest request);

  /** 商家端：编辑本人场馆并重新提交审核。 */
  Venue update(Long venueId, MerchantVenueRequest request);

  /** 商家端：下架本人场馆，保留审核记录。 */
  void offline(Long venueId);

  /** 商家端：读取并校验当前商家名下的场馆。 */
  Venue getMine(Long venueId);

  /** 按主键读取场馆资料，调用方负责完成权限校验。 */
  Venue getById(Long venueId);
}
