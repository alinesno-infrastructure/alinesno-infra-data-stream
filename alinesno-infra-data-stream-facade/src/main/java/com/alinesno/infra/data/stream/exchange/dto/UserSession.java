package com.alinesno.infra.data.stream.exchange.dto;

import com.alibaba.fastjson.JSON;
import com.alinesno.infra.data.stream.exchange.common.util.Md5Utils;


/**
 * @author zhp
 * @Description:
 * @date 2020-07-13
 * @time 23:20
 */
public class UserSession {

  private Integer userid;

  private String name;

  private String password;


  public UserSession(Integer userid, String name, String password) {
    this.userid = userid;
    this.name = name;
    this.password = password;
  }

  public static String toJsonString(Integer userid, String name, String password) {
    return JSON.toJSONString(new UserSession(userid, name, Md5Utils.getMD5String(password)));
  }

  public static UserSession toUserSession(String json) {
    return JSON.parseObject(json, UserSession.class);
  }
}
