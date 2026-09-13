package com.example.booking.service;

import com.example.booking.domain.entity.Court;
import com.example.booking.domain.dto.MerchantCourtRequest;
import java.util.List;

public interface CourtService {

  /** 顾客端：某场馆的上架场地；venueId 传 null 表示不按场馆过滤 */
  List<Court> listOnlineByVenue(Long venueId);

  /** 商家端：当前登录商家名下的场地 */
  List<Court> listMine();

  /** 商家端：创建本人场馆下的场地，资源默认待审核且下架。 */
  Court create(MerchantCourtRequest request);

  /** 商家端：编辑本人场馆下的场地并重新提交审核。 */
  Court update(Long courtId, MerchantCourtRequest request);

  /** 商家端：下架本人场馆下的场地，保留审核记录。 */
  void offline(Long courtId);

  /** 商家端：读取并校验当前商家名下的单个上架场地 */
  Court getMine(Long courtId);
}
