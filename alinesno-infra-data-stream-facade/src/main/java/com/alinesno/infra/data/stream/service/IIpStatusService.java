package com.alinesno.infra.data.stream.service;

import com.alinesno.infra.common.facade.services.IBaseService;
import com.alinesno.infra.data.stream.entity.IpStatusEntity;

/**
 * IP状态Service接口
 *
 * @version 1.0.0
 * @author luoxiaodong
 */
public interface IIpStatusService extends IBaseService<IpStatusEntity> {
    void cancelIp();

    void updateHeartbeatBylocalIp();

    boolean isLeader();

    void registerIp();
}
