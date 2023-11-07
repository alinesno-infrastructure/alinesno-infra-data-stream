package com.alinesno.infra.data.flink.exchange.rpc;

import com.alinesno.infra.data.flink.exchange.enums.YarnStateEnum;
import com.alinesno.infra.data.flink.exchange.rpc.model.JobInfo;

/**
 * @author zhp
 * @Description:
 * @date 2020-08-06
 * @time 20:15
 */
public interface YarnRestRpcAdapter {

  /**
   * 通过任务名称获取yarn 的appId
   *
   * @author zhp
   * @date 2020-08-06
   * @time 20:18
   */
  String getAppIdByYarn(String jobName, String queueName, Long operatorId);

  /**
   * 通过http杀掉一个任务
   *
   * @author zhp
   * @date 2020-08-06
   * @time 20:50
   */
  void stopJobByJobId(String appId);


  /**
   * 查询yarn 上某任务状态
   *
   * @author zhp
   * @date 2020-08-07
   * @time 21:36
   */
  YarnStateEnum getJobStateByJobId(String appId);


  /**
   * per yarn 模式下获取任务新状态
   *
   * @author zhp
   * @date 2020-09-18
   * @time 01:15
   */
  JobInfo getJobInfoForPerYarnByAppId(String appId);


  /**
   * per yarn 模式下 取消任务
   *
   * @param appId (yarn上的appId)
   * @param jobId (flink上的id)
   * @author zhp
   * @date 2020-09-18
   * @time 00:01
   */
  void cancelJobForYarnByAppId(String appId, String jobId);


  /**
   * per yarn 模式下 获取SavepointPath 地址
   * <p>
   * 通过checkpoint 接口获取Savepoint地址
   *
   * @author zhp
   * @date 2020-09-21
   * @time 02:44
   */
  String getSavepointPath(String appId, String jobId);


}
