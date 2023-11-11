package com.alinesno.infra.data.flink.exchange.ao.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.alinesno.infra.common.facade.response.ResultCodeEnum;
import com.alinesno.infra.data.flink.commom.constant.SystemConstant;
import com.alinesno.infra.data.flink.commom.enums.JobTypeEnum;
import com.alinesno.infra.data.flink.exchange.ao.JobBaseServiceAO;
import com.alinesno.infra.data.flink.exchange.common.MessageConstants;
import com.alinesno.infra.data.flink.exchange.common.ResponseBean;
import com.alinesno.infra.data.flink.exchange.common.SystemConstants;
import com.alinesno.infra.data.flink.exchange.common.TipsConstants;
import com.alinesno.infra.data.flink.exchange.common.util.*;
import com.alinesno.infra.data.flink.exchange.config.CustomConfig;
import com.alinesno.infra.data.flink.exchange.config.JobThreadPool;
import com.alinesno.infra.data.flink.exchange.dto.JobConfigDTO;
import com.alinesno.infra.data.flink.exchange.dto.JobRunLogDTO;
import com.alinesno.infra.data.flink.exchange.dto.JobRunParamDTO;
import com.alinesno.infra.data.flink.exchange.dto.SystemConfigDTO;
import com.alinesno.infra.data.flink.exchange.enums.*;
import com.alinesno.infra.data.flink.exchange.exceptions.BizException;
import com.alinesno.infra.data.flink.exchange.rpc.CommandRpcClinetAdapter;
import com.alinesno.infra.data.flink.exchange.rpc.FlinkRestRpcAdapter;
import com.alinesno.infra.data.flink.exchange.rpc.YarnRestRpcAdapter;
import com.alinesno.infra.data.flink.exchange.rpc.model.JobStandaloneInfo;
import com.alinesno.infra.data.flink.exchange.utils.SystemInfoUtil;
import com.alinesno.infra.data.flink.service.IJobConfigService;
import com.alinesno.infra.data.flink.service.IJobRunLogService;
import com.alinesno.infra.data.flink.service.ISystemConfigService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zhp
 * @Description:
 * @date 2021/3/28
 * @time 10:02
 */
@Component
public class JobBaseServiceAOImpl implements JobBaseServiceAO {

  // 日志记录
  private static final Logger log = LoggerFactory.getLogger(JobBaseServiceAOImpl.class);

  public static final ThreadLocal<String> THREADAPPID = new ThreadLocal<String>();

  @Autowired
  private IJobRunLogService jobRunLogService;


  @Autowired
  private YarnRestRpcAdapter yarnRestRpcAdapter;

  @Autowired
  private ISystemConfigService systemConfigService;

  @Autowired
  private CommandRpcClinetAdapter commandRpcClinetAdapter;

  @Autowired
  private CustomConfig customConfig;

  @Autowired
  private IJobConfigService jobConfigService;

  @Autowired
  private FlinkRestRpcAdapter flinkRestRpcAdapter;

  @NacosInjected
  private ConfigService configService;

  @NacosValue(value = "${nacos.config.data-id:}", autoRefreshed = true)
  private String nacosConfigDataId;

  @NacosValue(value = "${nacos.config.group:}", autoRefreshed = true)
  private String nacosConfigGroup;

  @Override
  public void checkStart(JobConfigDTO jobConfigDTO) {
    if (jobConfigDTO == null) {
      throw new BizException(SysErrorEnum.JOB_CONFIG_JOB_IS_NOT_EXIST.getErrorMsg());
    }
    if (JobConfigStatus.RUN.getCode().equals(jobConfigDTO.getStatus().getCode())) {
      throw new BizException("任务运行中请先停止任务");
    }
    if (jobConfigDTO.getStatus().equals(JobConfigStatus.STARTING)) {
      throw new BizException("任务正在启动中 请稍等..");
    }
    if (jobConfigDTO.getIsOpen().intValue() == YN.N.getValue()) {
      throw new BizException("请先开启任务");
    }
    switch (jobConfigDTO.getDeployModeEnum()) {
      case YARN_PER:
      case YARN_APPLICATION:
        this.checYarnQueue(jobConfigDTO);
        break;
      //TODO
      default:

    }

    //SysConfigEnumType
    Map<String, String> systemConfigMap = SystemConfigDTO
        .toMap(systemConfigService.getSystemConfig(SysConfigEnumType.SYS));
    this.checkSysConfig(systemConfigMap, jobConfigDTO.getDeployModeEnum());


  }

  @Override
  public void checkSavepoint(JobConfigDTO jobConfigDTO) {
    if (jobConfigDTO == null) {
      throw new BizException(SysErrorEnum.JOB_CONFIG_JOB_IS_NOT_EXIST.getErrorMsg());
    }

    if (JobTypeEnum.SQL_BATCH.equals(jobConfigDTO.getJobTypeEnum())) {
      log.warn(MessageConstants.MESSAGE_010, jobConfigDTO.getJobName());
      throw new BizException(MessageConstants.MESSAGE_010);
    }

    if (StringUtils.isEmpty(jobConfigDTO.getFlinkCheckpointConfig())
        && DeployModeEnum.STANDALONE.equals(jobConfigDTO.getDeployModeEnum())) {
      log.error(MessageConstants.MESSAGE_004, jobConfigDTO);
      throw new BizException(MessageConstants.MESSAGE_004);
    }
    if (StringUtils.isEmpty(jobConfigDTO.getJobId())) {
      log.warn(MessageConstants.MESSAGE_005, jobConfigDTO.getJobName());
      throw new BizException(MessageConstants.MESSAGE_005);
    }


  }

  @Override
  public ResponseBean checkClose(JobConfigDTO jobConfigDTO) {
    ResponseBean result = new ResponseBean();
    result.setCode(ResultCodeEnum.SUCCESS);
    if ( jobConfigDTO.getStatus().equals(JobConfigStatus.RUN) ) {
      result.setCode(ResultCodeEnum.FAIL);
      result.setMessage(MessageConstants.MESSAGE_002);
    }
    if ( jobConfigDTO.getStatus().equals(JobConfigStatus.STARTING) ) {
      result.setCode(ResultCodeEnum.FAIL);
      result.setMessage(MessageConstants.MESSAGE_003);
    }
    return result;
  }

  @Override
  public Long insertJobRunLog(JobConfigDTO jobConfigDTO) {
    JobRunLogDTO jobRunLogDTO = new JobRunLogDTO();
    jobRunLogDTO.setDeployMode(jobConfigDTO.getDeployModeEnum().name());
    jobRunLogDTO.setLocalLog(MessageConstants.MESSAGE_001);
    jobRunLogDTO.setJobConfigId(jobConfigDTO.getId());
    jobRunLogDTO.setStartTime(new Date());
    jobRunLogDTO.setJobName(jobConfigDTO.getJobName());
    jobRunLogDTO.setJobId(jobConfigDTO.getJobId());
    jobRunLogDTO.setJobStatus(JobStatusEnum.STARTING.name());
    jobRunLogDTO.setCreator(jobConfigDTO.getCreator());
    jobRunLogDTO.setEditor(jobConfigDTO.getEditor());
    jobRunLogDTO.setRunIp(IpUtil.getInstance().getLocalIP());
    return jobRunLogService.insertJobRunLog(jobRunLogDTO);
  }

  @Override
  public JobRunParamDTO writeSqlToFile(JobConfigDTO jobConfigDTO) {
    if (configService != null && StringUtils.isNotEmpty(nacosConfigDataId)
        && StringUtils.isNotEmpty(nacosConfigGroup)) {
      replaceNacosParameters(jobConfigDTO);
    }

    Map<String, String> systemConfigMap = SystemConfigDTO
        .toMap(systemConfigService.getSystemConfig(SysConfigEnumType.SYS));
    String fileName = FileUtils.createFileName(String.valueOf(jobConfigDTO.getId()));

    String sqlPath = FileUtils
        .getSqlHome(systemConfigMap.get(SysConfigEnum.FLINK_STREAMING_PLATFORM_WEB_HOME.getKey()))
        + fileName;

    FileUtils.writeText(sqlPath, jobConfigDTO.getFlinkSql(), Boolean.FALSE);

    String sqlUrl = customConfig.getUrlForReadLocal() + fileName;

    return JobRunParamDTO.buildJobRunParam(systemConfigMap, jobConfigDTO, sqlPath, sqlUrl);
  }

  //CHECKSTYLE:OFF
  @Override
  public void aSyncExecJob(JobRunParamDTO jobRunParamDTO, JobConfigDTO jobConfigDTO,
                           long jobRunLogId, String savepointPath) {
    if (configService != null && StringUtils.isNotEmpty(nacosConfigDataId)
        && StringUtils.isNotEmpty(nacosConfigGroup)) {
      replaceNacosParameters(jobConfigDTO);
    }
    ThreadPoolExecutor threadPoolExecutor = JobThreadPool.getInstance().getThreadPoolExecutor();
    threadPoolExecutor.execute(new Runnable() {
      @Override
      public void run() {
        String jobStatus = JobStatusEnum.SUCCESS.name();
        String appId = "";
        THREADAPPID.set(appId);
        boolean success = true;
        StringBuilder localLog = new StringBuilder()
            .append("开始提交任务：")
            .append(DateUtil.now()).append(SystemConstant.LINE_FEED)
            .append("二方jar: ").append(SystemConstant.LINE_FEED);
        if (jobConfigDTO.getExtJarPath() != null) {
          localLog.append(jobConfigDTO.getExtJarPath());
        }

        localLog.append(SystemConstant.LINE_FEED)
            .append("客户端IP：").append(IpUtil.getInstance().getLocalIP())
            .append(SystemConstant.LINE_FEED);
        try {
          String command = "";
          jobConfigDTO.setDownloadUrl(customConfig.getUrlForDown());
          String hadoopClassPath = SystemInfoUtil.getEnv(SystemConstant.HADOOP_CLASSPATH);
          log.info("HADOOP_CLASSPATH={}",hadoopClassPath);
          localLog.append("HADOOP_CLASSPATH=").append(hadoopClassPath)
              .append(SystemConstant.LINE_FEED);
          //如果是自定义提交jar模式下载文件到本地
          this.downJar(jobRunParamDTO, jobConfigDTO);
          switch (jobConfigDTO.getDeployModeEnum()) {
            case YARN_PER:
            case YARN_APPLICATION:

              if (StringUtils.isEmpty(hadoopClassPath)){
                throw new BizException("yarn 模式 需要在客户端配置 HADOOP_CLASSPATH环境变量");
              }
              log.info("YARN模式下 HADOOP_CLASSPATH={}", hadoopClassPath);

              //1、构建执行命令
              command = CommandUtil.buildRunCommandForYarnCluster(jobRunParamDTO,
                  jobConfigDTO, savepointPath);
              //2、提交任务
              appId = this.submitJobForYarn(command, jobConfigDTO, localLog);
              THREADAPPID.set(appId);
              break;
            case LOCAL:
            case STANDALONE:
              String address = systemConfigService
                  .getFlinkAddress(jobConfigDTO.getDeployModeEnum());
              log.info("flink 远程提交地址是 address={}", address);
              localLog.append("address=").append(address).append(SystemConstant.LINE_FEED);
              //1、构建执行命令
              command = CommandUtil
                  .buildRunCommandForCluster(jobRunParamDTO, jobConfigDTO, savepointPath, address);
              //2、提交任务
              appId = this.submitJobForStandalone(command, jobConfigDTO, localLog);
              break;
            default:
              log.warn("不支持的模式 {}", jobConfigDTO.getDeployModeEnum());
          }
        } catch (Exception e) {
          log.error("任务[" + jobConfigDTO.getId() + "]执行有异常！", e);
          localLog.append(e).append(errorInfoDir(jobConfigDTO.getCreator()));
          success = false;
          jobStatus = JobStatusEnum.FAIL.name();
        } finally {
          localLog.append("\n启动结束时间: ").append(DateUtil.now()).append(SystemConstant.LINE_FEED);
          if (success) {
            localLog.append(
                "######客户端提交任务 成功 （客户端提交成功不代表任务在集群运行成功，具体请查看集群任务）############################## ");
          } else {
            localLog.append("######启动结果是 失败############################## ");
          }
          if (StringUtils.isBlank(appId)) { // 解决任务异常，但已经生成了appID，但没有传递给上层调用方法的问题
            appId = THREADAPPID.get();
            log.info("任务[{}]执行有异常情况，appid = {}", jobConfigDTO.getId(), appId);
          }
          this.updateStatusAndLog(jobConfigDTO, jobRunLogId, jobStatus, localLog.toString(), appId);
        }
      }


      /**
       *下载文件到本地并且setMainJarPath
       * @author zhp
       * @date 2021/3/28
       * @time 14:58
       */
      private void downJar(JobRunParamDTO jobRunParamDTO, JobConfigDTO jobConfigDTO) {
        if (JobTypeEnum.JAR.getCode() == jobConfigDTO.getJobTypeEnum().getCode()) {
          String pathName = null;
          if (MatcherUtils.isHttpsOrHttp(jobConfigDTO.getCustomJarUrl())) {
            String savePath = jobRunParamDTO.getSysHome() + "tmp/udf_jar/";
            try {
              pathName = UrlUtil.downLoadFromUrl(jobConfigDTO.getCustomJarUrl(), savePath);
            } catch (Exception e) {
              log.error("文件下载失败 {} path={}", jobConfigDTO.getCustomJarUrl(), savePath, e);
              throw new BizException("文件下载失败");
            }
          } else {
            pathName =
                systemConfigService.getUploadJarsPath() + SystemConstant.VIRGULE + jobConfigDTO.getCustomJarUrl();
          }
          log.info("JAR模式下 pathName={}", pathName);
          jobRunParamDTO.setMainJarPath(pathName);

        }
      }

      /**
       *错误日志目录提示
       * @author zhp
       * @date 2020-10-19
       * @time 21:47
       */
      private String errorInfoDir(long operatorId) {
        StringBuilder errorTips = new StringBuilder(SystemConstant.LINE_FEED)
            .append(SystemConstant.LINE_FEED)
            .append("（重要）请登陆服务器分别查看下面两个目录下的错误日志")
            .append(IpUtil.getInstance().getLocalIP()).append(SystemConstant.LINE_FEED)
            .append("web系统日志目录（web日志）：")
            .append(systemConfigService
                .getSystemConfigByKey(SysConfigEnum.FLINK_STREAMING_PLATFORM_WEB_HOME.getKey()))
            .append("logs/error.log").append(SystemConstant.LINE_FEED)
            .append("flink提交日志目录（flink客户端日志：")
            .append(systemConfigService.getSystemConfigByKey(SysConfigEnum.FLINK_HOME.getKey()))
            .append("log/)").append(SystemConstant.LINE_FEED)
            .append(SystemConstant.LINE_FEED)
            .append(SystemConstant.LINE_FEED);
        return errorTips.toString();
      }


      /**
       * 更新日志、更新配置信息
       * @param jobConfig
       * @param jobRunLogId
       * @param jobStatus
       * @param localLog
       * @param appId
       */
      private void updateStatusAndLog(JobConfigDTO jobConfig, long jobRunLogId,
          String jobStatus, String localLog, String appId) {
        try {
          JobConfigDTO jobConfigDTO = new JobConfigDTO();
          jobConfigDTO.setId(jobConfig.getId());
          jobConfigDTO.setJobId(appId);
          JobRunLogDTO jobRunLogDTO = new JobRunLogDTO();
          jobRunLogDTO.setId(jobRunLogId);
          jobRunLogDTO.setJobId(appId);
          if (JobStatusEnum.SUCCESS.name().equals(jobStatus) && !StringUtils.isEmpty(appId)) {

            //批任务提交完成后算成功
            if (JobTypeEnum.SQL_BATCH.equals(jobConfig.getJobTypeEnum())) {
              jobConfigDTO.setStatus(JobConfigStatus.SUCCESS);
            } else {
              jobConfigDTO.setStatus(JobConfigStatus.RUN);
            }

            jobConfigDTO.setLastStartTime(new Date());
            if (DeployModeEnum.LOCAL.equals(jobConfig.getDeployModeEnum())
                || DeployModeEnum.STANDALONE.equals(jobConfig.getDeployModeEnum())) {
              jobRunLogDTO.setRemoteLogUrl(
                  systemConfigService.getFlinkHttpAddress(jobConfig.getDeployModeEnum())
                      + SystemConstants.HTTP_STANDALONE_APPS + jobConfigDTO.getJobId());
            }
            if (DeployModeEnum.YARN_PER.equals(jobConfig.getDeployModeEnum())) {
              jobRunLogDTO.setRemoteLogUrl(systemConfigService.getYarnRmHttpAddress()
                  + SystemConstants.HTTP_YARN_CLUSTER_APPS + jobConfigDTO.getJobId());
            }

          } else {
            jobConfigDTO.setStatus(JobConfigStatus.FAIL);
          }
          jobConfigService.updateJobConfigById(jobConfigDTO);

          jobRunLogDTO.setJobStatus(jobStatus);
          jobRunLogDTO.setLocalLog(localLog);
          jobRunLogDTO.setEndTime(new Date());
          jobRunLogService.updateJobRunLogById(jobRunLogDTO);

          //最后更新一次日志 (更新日志和更新信息分开 防止日志更新失败导致相关状态更新也失败)
          jobRunLogService.updateLogById(localLog, jobRunLogId);
        } catch (Exception e) {
          log.error(" localLog.length={} 异步更新数据失败：", localLog.length(), e);
        }
      }

      private String submitJobForStandalone(String command, JobConfigDTO jobConfig,
          StringBuilder localLog)
          throws Exception {
        String appId = commandRpcClinetAdapter
            .submitJob(command, localLog, jobRunLogId, jobConfig.getDeployModeEnum());
        JobStandaloneInfo jobStandaloneInfo = flinkRestRpcAdapter.getJobInfoForStandaloneByAppId(appId, jobConfig.getDeployModeEnum() );

        if (jobStandaloneInfo == null || StringUtils.isNotEmpty(jobStandaloneInfo.getErrors())) {
          log.error("[submitJobForStandalone] is error jobStandaloneInfo={}", jobStandaloneInfo);
          localLog.append("\n 任务失败 appId=" + appId);
          throw new BizException("任务失败");
        } else {
          if (!SystemConstants.STATUS_RUNNING.equals(jobStandaloneInfo.getState())
              && !SystemConstants.STATUS_FINISHED.equals(jobStandaloneInfo.getState())) {
            localLog.append("\n 任务失败 appId=" + appId).append("状态是：" + jobStandaloneInfo.getState());
            throw new BizException("[submitJobForStandalone]任务失败");
          }
        }
        return appId;
      }

      private String submitJobForYarn(String command, JobConfigDTO jobConfigDTO,
          StringBuilder localLog)
          throws Exception {
        commandRpcClinetAdapter
            .submitJob(command, localLog, jobRunLogId, jobConfigDTO.getDeployModeEnum());
        return yarnRestRpcAdapter.getAppIdByYarn(jobConfigDTO.getJobName(),
            YarnUtil.getQueueName(jobConfigDTO.getFlinkRunConfig()), jobConfigDTO.getCreator());
      }

    });
  }
  //CHECKSTYLE:ON

  private void checYarnQueue(JobConfigDTO jobConfigDTO) {
    try {
      String queueName = YarnUtil.getQueueName(jobConfigDTO.getFlinkRunConfig());
      if (StringUtils.isEmpty(queueName)) {
        throw new BizException("无法获取队列名称，请检查你的 flink运行配置参数");
      }
      String appId = yarnRestRpcAdapter.getAppIdByYarn(jobConfigDTO.getJobName(), queueName, jobConfigDTO.getCreator());
      if (StringUtils.isNotEmpty(appId)) {
        throw new BizException("该任务在yarn上有运行，请到集群上取消任务后再运行 任务名称是:"
            + jobConfigDTO.getJobName() + " 队列名称是:" + queueName + TipsConstants.TIPS_1);
      }
    } catch (BizException e) {
      if (SysErrorEnum.YARN_CODE.getCode().equals(e.getCode())) {
        log.info(e.getErrorMsg());
      } else {
        throw e;
      }
    } catch (Exception e) {
      throw new BizException(e.getMessage());
    }
  }

  private void checkSysConfig(Map<String, String> systemConfigMap, DeployModeEnum deployModeEnum) {
    if (systemConfigMap == null) {
      throw new BizException(SysErrorEnum.SYSTEM_CONFIG_IS_NULL.getErrorMsg());
    }
    if (!systemConfigMap.containsKey(SysConfigEnum.FLINK_HOME.getKey())) {
      throw new BizException(SysErrorEnum.SYSTEM_CONFIG_IS_NULL_FLINK_HOME.getErrorMsg());
    }

    if (DeployModeEnum.YARN_PER.equals(deployModeEnum) || DeployModeEnum.YARN_APPLICATION
        .equals(deployModeEnum)) {
      if (!systemConfigMap.containsKey(SysConfigEnum.YARN_RM_HTTP_ADDRESS.getKey())) {
        throw new BizException(SysErrorEnum.SYSTEM_CONFIG_IS_NULL_YARN_RM_HTTP_ADDRESS.getErrorMsg());
      }
    }
    if (!systemConfigMap.containsKey(SysConfigEnum.FLINK_STREAMING_PLATFORM_WEB_HOME.getKey())) {
      throw new BizException(SysErrorEnum.SYSTEM_CONFIG_IS_NULL_FLINK_STREAMING_PLATFORM_WEB_HOME.getErrorMsg());
    }
  }

  /**
   * 正则表达式，区配参数：${xxxx}
   */
  private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{[\\w.-]+\\}");

  /**
   * 使用Nacos配置替换脚本中的参数
   *
   * @param jobConfigDTO
   * @author wxj
   * @date 2021年12月29日 下午3:02:46
   * @version V1.0
   */
  private void replaceNacosParameters(JobConfigDTO jobConfigDTO) {
    try {
      String configInfo = configService.getConfig(nacosConfigDataId, nacosConfigGroup, 3000);
      Properties properties = new Properties();
      properties.load(new StringReader(configInfo));
      jobConfigDTO.setFlinkRunConfig(replaceParamter(properties, jobConfigDTO.getFlinkRunConfig()));
      jobConfigDTO.setFlinkCheckpointConfig(
          replaceParamter(properties, jobConfigDTO.getFlinkCheckpointConfig()));
      jobConfigDTO.setExtJarPath(replaceParamter(properties, jobConfigDTO.getExtJarPath()));
      jobConfigDTO.setFlinkSql(replaceParamter(properties, jobConfigDTO.getFlinkSql()));
      jobConfigDTO.setCustomArgs(replaceParamter(properties, jobConfigDTO.getCustomArgs()));
      jobConfigDTO
          .setCustomMainClass(replaceParamter(properties, jobConfigDTO.getCustomMainClass()));
      jobConfigDTO.setCustomJarUrl(replaceParamter(properties, jobConfigDTO.getCustomJarUrl()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String replaceParamter(Properties properties, String text) {
    if (text == null) {
      return null;
    }
    Matcher m = PARAM_PATTERN.matcher(text);
    while (m.find()) {
      String param = m.group();
      String key = param.substring(2, param.length() - 1);
      String value = (String) properties.get(key);
      if (value != null) {
        text = text.replace(param, value);
      }
    }
    return text;
  }
}