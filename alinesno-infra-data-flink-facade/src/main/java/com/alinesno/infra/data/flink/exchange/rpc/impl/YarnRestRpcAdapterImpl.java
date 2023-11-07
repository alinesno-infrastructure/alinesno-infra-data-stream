package com.alinesno.infra.data.flink.exchange.rpc.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alinesno.infra.data.flink.exchange.common.FlinkYarnRestUriConstants;
import com.alinesno.infra.data.flink.exchange.common.SystemConstants;
import com.alinesno.infra.data.flink.exchange.common.util.HttpUtil;
import com.alinesno.infra.data.flink.exchange.dto.JobConfigDTO;
import com.alinesno.infra.data.flink.exchange.enums.SysErrorEnum;
import com.alinesno.infra.data.flink.exchange.enums.YarnStateEnum;
import com.alinesno.infra.data.flink.exchange.exceptions.BizException;
import com.alinesno.infra.data.flink.exchange.rpc.YarnRestRpcAdapter;
import com.alinesno.infra.data.flink.exchange.rpc.model.JobInfo;
import com.alinesno.infra.data.flink.exchange.to.AppTO;
import com.alinesno.infra.data.flink.exchange.to.YarnAppInfo;
import com.alinesno.infra.data.flink.service.ISystemConfigService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author zhp
 * @Description:
 * @date 2020-08-06
 * @time 20:15
 */
@Service
public class YarnRestRpcAdapterImpl implements YarnRestRpcAdapter {

  // 日志记录
  private static final Logger log = LoggerFactory.getLogger(YarnRestRpcAdapterImpl.class);

  private static final String BODY_HTTP_KILL = "{\"state\":\"KILLED\"}";

  @Autowired
  private ISystemConfigService systemConfigService;

  //TODO 这个方法设计不好 需要改造
  @Override
  public String getAppIdByYarn(String jobName, String queueName, Long operatorId) {
    if (StringUtils.isEmpty(jobName)) {
      throw new BizException(SysErrorEnum.PARAM_IS_NULL);
    }
    String url =
        systemConfigService.getYarnRmHttpAddress() + SystemConstants.buildHttpQuery(queueName);
    RestTemplate restTemplate = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_1_M);
    log.info("请求参数  url={}", url);
    String res = restTemplate.getForObject(url, String.class);

    YarnAppInfo yarnAppInfo = JSON.parseObject(res, YarnAppInfo.class);

    this.check(yarnAppInfo, queueName, jobName, url);

    for (AppTO appTO : yarnAppInfo.getApps().getApp()) {
      if (JobConfigDTO.buildRunName(jobName).equals(appTO.getName())) {
        if (SystemConstants.STATUS_RUNNING.equals(appTO.getState())) {
          log.info("任务信息 appTO={}", appTO);
          return appTO.getId();
        } else {
          log.error("任务运行状态失败 状态是 {}", appTO.getState());
        }
      }
    }
    throw new BizException("yarn队列" + queueName + "中没有找到运行的任务 name="
        + JobConfigDTO.buildRunName(jobName), SysErrorEnum.YARN_CODE.getCode());
  }


  @Override
  public void stopJobByJobId(String appId) {


    log.info("执行stopJobByJobId appId={}", appId);
    if (StringUtils.isEmpty(appId)) {
      throw new BizException(SysErrorEnum.PARAM_IS_NULL_YARN_APPID);
    }
    String url = HttpUtil.buildUrl(systemConfigService.getYarnRmHttpAddress(),
        SystemConstants.HTTP_YARN_APPS + appId + "/state");
    log.info("请求关闭 URL ={}", url);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> httpEntity = new HttpEntity(BODY_HTTP_KILL, headers);
    RestTemplate restTemplate = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_1_M);
    restTemplate.put(url, httpEntity);
  }

  @Override
  public YarnStateEnum getJobStateByJobId(String appId) {

    if (StringUtils.isEmpty(appId)) {
      throw new BizException(SysErrorEnum.PARAM_IS_NULL_YARN_APPID);
    }

    String url = HttpUtil.buildUrl(systemConfigService.getYarnRmHttpAddress(),
        SystemConstants.HTTP_YARN_APPS + appId + "/state");
    RestTemplate restTemplate = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_1_M);
    String res = restTemplate.getForObject(url, String.class);
    if (StringUtils.isEmpty(res)) {
      log.error("请求失败:返回结果为空 url={}", url);
      throw new BizException(SysErrorEnum.HTTP_REQUEST_IS_NULL);
    }
    return YarnStateEnum.getYarnStateEnum(String.valueOf(JSON.parseObject(res).get("state")));
  }

  @Override
  public JobInfo getJobInfoForPerYarnByAppId(String appId) {
    if (StringUtils.isEmpty(appId)) {
      throw new BizException(SysErrorEnum.HTTP_REQUEST_IS_NULL);
    }
    String res = null;
    try {
      String url = HttpUtil.buildUrl(systemConfigService.getYarnRmHttpAddress(),
          FlinkYarnRestUriConstants.getUriJobsForYarn(appId));
      log.info("[getJobInfoForPerYarnByAppId]请求参数 appId={} url={}", appId, url);
      res = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_1_M).getForObject(url, String.class);
      if (StringUtils.isEmpty(res)) {
        return null;
      }
      JSONArray jsonArray = (JSONArray) JSON.parseObject(res).get("jobs");
      JSONObject jsonObject = (JSONObject) jsonArray.get(0);
      JobInfo jobInfo = new JobInfo();
      jobInfo.setId((String) jsonObject.get("id"));
      jobInfo.setStatus((String) jsonObject.get("status"));
      return jobInfo;
    } catch (Exception e) {
      log.error("[getJobInfoForPerYarnByAppId] 错误  ", e);
    }
    return null;
  }

  @Override
  public void cancelJobForYarnByAppId(String appId, String jobId) {
    if (StringUtils.isEmpty(appId) || StringUtils.isEmpty(jobId)) {
      throw new BizException(SysErrorEnum.PARAM_IS_NULL);
    }
    String url = HttpUtil.buildUrl(systemConfigService.getYarnRmHttpAddress(),
        FlinkYarnRestUriConstants.getUriCancelForYarn(appId, jobId));
    log.info("[cancelJobByAppId]请求参数 appId={} jobId={} url={}", appId, jobId, url);
    String res = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_1_M).getForObject(url, String.class);
    log.info("[cancelJobByAppId]请求参数结果: res={}", res);
  }


  @Override
  public String getSavepointPath(String appId, String jobId) {
    if (StringUtils.isEmpty(appId) || StringUtils.isEmpty(jobId)) {
      throw new BizException(SysErrorEnum.PARAM_IS_NULL);
    }
    String url = HttpUtil.buildUrl(systemConfigService.getYarnRmHttpAddress(),
        FlinkYarnRestUriConstants.getUriCheckpointForYarn(appId, jobId));
    log.info("[getSavepointPath]请求参数 appId={} jobId={} url={}", appId, jobId, url);
    String res = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_5_M).getForObject(url, String.class);
    log.info("[getSavepointPath]请求参数结果: res={}", res);
    if (StringUtils.isEmpty(res)) {
      return null;
    }
    try {
      JSONObject jsonObject = (JSONObject) JSON.parseObject(res).get("latest");
      JSONObject savepoint = (JSONObject) jsonObject.get("savepoint");
      if (savepoint == null) {
        return null;
      }
      return (String) savepoint.get("external_path");
    } catch (Exception e) {
      log.error("json 异常 res={}", res, e);
    }

    return null;
  }

  private void check(YarnAppInfo yarnAppInfo, String queueName, String jobName, String url) {
    if (yarnAppInfo == null) {
      log.error("在队列" + queueName + "没有找到任何yarn上的任务 url={}", url);
      throw new BizException("yarn队列" + queueName + "中没有找到运行的任务 name="
          + JobConfigDTO.buildRunName(jobName), SysErrorEnum.YARN_CODE.getCode());
    }
    if (yarnAppInfo.getApps() == null) {
      log.error("yarnAppInfo.getApps() is null", yarnAppInfo);
      throw new BizException("yarn队列" + queueName + "中没有找到运行的任务 name="
          + JobConfigDTO.buildRunName(jobName), SysErrorEnum.YARN_CODE.getCode());
    }
    if (yarnAppInfo.getApps().getApp() == null || yarnAppInfo.getApps().getApp().size() <= 0) {
      log.error("yarnAppInfo.getApps().getApp() is null", yarnAppInfo);
      throw new BizException("yarn队列" + queueName + "中没有找到运行的任务 name="
          + JobConfigDTO.buildRunName(jobName), SysErrorEnum.YARN_CODE.getCode());
    }
  }

}
