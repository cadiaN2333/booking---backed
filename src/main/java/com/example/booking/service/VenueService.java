package com.example.booking.service;

import com.example.booking.domain.entity.Venue;
import java.util.List;

public interface VenueService {

  /** 顾客端：全部上架场馆 */
  List<Venue> listOnline();

  /** 商家端：当前登录商家名下的场馆 */
  List<Venue> listMine();
}
