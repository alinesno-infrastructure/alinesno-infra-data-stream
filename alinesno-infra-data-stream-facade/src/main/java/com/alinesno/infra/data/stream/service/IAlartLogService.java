package com.alinesno.infra.data.stream.service;

import com.alinesno.infra.common.facade.services.IBaseService;
import com.alinesno.infra.data.stream.entity.AlartLogEntity;
import com.alinesno.infra.data.stream.exchange.dto.AlartLogDTO;

/**
 * 告警日志Service接口
 *
 * @version 1.0.0
 * @author luoxiaodong
 */
public interface IAlartLogService extends IBaseService<AlartLogEntity> {
    void addAlartLog(AlartLogDTO alartLogDTO);

    /**
     * 按照id查询
     *
     * @author zhp
     * @date 2020-09-25
     * @time 21:49
     */
    AlartLogDTO findLogById(Long id);


    void deleteLogByJobConfigId(Long jobConfigId);
}
