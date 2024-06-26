package com.alinesno.infra.data.stream.exchange.rpc.impl;

import cn.hutool.core.date.DateUtil;
import com.alinesno.infra.data.stream.commom.constant.SystemConstant;
import com.alinesno.infra.data.stream.exchange.ao.impl.JobBaseServiceAOImpl;
import com.alinesno.infra.data.stream.exchange.common.SystemConstants;
import com.alinesno.infra.data.stream.exchange.common.TipsConstants;
import com.alinesno.infra.data.stream.exchange.common.util.CommandUtil;
import com.alinesno.infra.data.stream.exchange.config.WaitForPoolConfig;
import com.alinesno.infra.data.stream.exchange.enums.DeployModeEnum;
import com.alinesno.infra.data.stream.exchange.enums.SysConfigEnum;
import com.alinesno.infra.data.stream.exchange.exceptions.BizException;
import com.alinesno.infra.data.stream.exchange.rpc.CommandRpcClinetAdapter;
import com.alinesno.infra.data.stream.service.IJobRunLogService;
import com.alinesno.infra.data.stream.service.ISystemConfigService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author zhp
 * @Description:
 * @date 2020-09-18
 * @time 20:13
 */
@Service
public class CommandRpcClinetAdapterImpl implements CommandRpcClinetAdapter {

  // 日志记录
  private static final Logger log = LoggerFactory.getLogger(CommandRpcClinetAdapterImpl.class);

  private static final long INTERVAL_TIME_TWO = 1000 * 2;

  @Autowired
  private IJobRunLogService jobRunLogService;


  @Autowired
  private ISystemConfigService systemConfigService;


  @Override
  public String submitJob(String command, StringBuilder localLog, Long jobRunLogId,
      DeployModeEnum deployModeEnum) throws Exception {
    log.info("任务提交命令:{} ", command);
    localLog.append("任务提交命令：").append(command).append(SystemConstant.LINE_FEED);
    Process pcs = Runtime.getRuntime().exec(command);

    //清理错误日志
    this.clearLogStream(pcs.getErrorStream(),
        String.format("%s#startForLocal-error#%s", DateUtil.now(),
            deployModeEnum.name()));
    String appId = this
        .clearInfoLogStream(pcs.getInputStream(), localLog, jobRunLogId, deployModeEnum);
    int rs = pcs.waitFor();
    localLog.append("提交命令的返回值=").append(rs).append(SystemConstant.LINE_FEED);
    jobRunLogService.updateLogById(localLog.toString(), jobRunLogId);
    if (rs != 0) {
      localLog.append("执行异常!提交命令的返回值不为0,返回值=").append(rs).append("   appId=").append(appId).append(SystemConstant.LINE_FEED);
      throw new RuntimeException("执行异常!提交命令的返回值不为0,返回值=" + rs);
    }

    return appId;
  }


  @Override
  public void savepointForPerYarn(String jobId, String targetDirectory, String yarnAppId, Long operatorId)
      throws Exception {
    String command = CommandUtil.buildSavepointCommandForYarn(jobId, targetDirectory, yarnAppId,
        systemConfigService.getSystemConfigByKey(SysConfigEnum.FLINK_HOME.getKey()));
    log.info("[savepointForPerYarn] command={}", command);
    this.execSavepoint(command);

  }

  @Override
  public void savepointForPerCluster(String jobId, String targetDirectory, Long operatorId) throws Exception {
    String command = CommandUtil.buildSavepointCommandForCluster(jobId, targetDirectory,
        systemConfigService.getSystemConfigByKey(SysConfigEnum.FLINK_HOME.getKey()));
    log.info("[savepointForPerCluster] command={}", command);
    this.execSavepoint(command);
  }


  private void execSavepoint(String command) throws Exception {
    Process pcs = Runtime.getRuntime().exec(command);
    //消费正常日志
    this.clearLogStream(pcs.getInputStream(),
        String.format("%s-savepoint-success", DateUtil.now()));
    //消费错误日志
    this.clearLogStream(pcs.getErrorStream(), String.format("%s-savepoint-error", DateUtil.now()));

    int rs = pcs.waitFor();
    if (rs != 0) {
      throw new Exception("[savepointForPerYarn]执行savepoint失败!提交命令的返回值不为0,返回值为:" + rs);
    }
  }

  /**
   * 清理pcs.waitFor()日志防止死锁
   *
   * @author zhp
   * @date 2021/3/28
   * @time 11:15
   */
  private void clearLogStream(InputStream stream, final String threadName) {
    WaitForPoolConfig.getInstance().getThreadPoolExecutor().execute(() -> {
          BufferedReader reader = null;
          try {
            Thread.currentThread().setName(threadName);
            String result = null;
            reader = new BufferedReader(new InputStreamReader(stream, SystemConstants.CODE_UTF_8));
            //按行读取
            while ((result = reader.readLine()) != null) {
              log.info(result);
            }
          } catch (Exception e) {
            log.error("threadName={}", threadName);
          } finally {
            this.close(reader, stream, "clearLogStream");
          }
        }
    );
  }

  /**
   * 启动日志输出并且从日志中获取成功后的jobId
   *
   * @author zhp
   * @date 2021/3/28
   * @time 11:15
   */
  private String clearInfoLogStream(InputStream stream, StringBuilder localLog, Long jobRunLogId,
      DeployModeEnum deployModeEnum) {

    String appId = null;
    BufferedReader reader = null;
    try {
      long lastTime = System.currentTimeMillis();
      String result = null;
      reader = new BufferedReader(new InputStreamReader(stream, SystemConstants.CODE_UTF_8));
      //按行读取
      while ((result = reader.readLine()) != null) {
        log.info("read={}", result);
        if (StringUtils.isEmpty(appId) && result.contains(SystemConstant.QUERY_JOBID_KEY_WORD)) {
          appId = result.replace(SystemConstant.QUERY_JOBID_KEY_WORD, SystemConstant.SPACE).trim();
          log.info("[job-submitted-success] 解析得到的appId是 {}  原始数据 :{}", appId, result);
          localLog.append("[job-submitted-success] 解析得到的appId是:")
              .append(appId).append(SystemConstant.LINE_FEED);
        }
        if (StringUtils.isEmpty(appId) && result
            .contains(SystemConstant.QUERY_JOBID_KEY_WORD_BACKUP)) {
          appId = result.replace(SystemConstant.QUERY_JOBID_KEY_WORD_BACKUP, SystemConstant.SPACE)
              .trim();
          log.info("[Job has been submitted with JobID] 解析得到的appId是 {}  原始数据 :{}", appId, result);
          localLog.append("[Job has been submitted with JobID] 解析得到的appId是:")
              .append(appId).append(SystemConstant.LINE_FEED);
        }
        localLog.append(result).append(SystemConstant.LINE_FEED);
        //每隔2s更新日志
        if (System.currentTimeMillis() >= lastTime + INTERVAL_TIME_TWO) {
          jobRunLogService.updateLogById(localLog.toString(), jobRunLogId);
          lastTime = System.currentTimeMillis();
        }
      }

      if (DeployModeEnum.YARN_APPLICATION == deployModeEnum
          || DeployModeEnum.YARN_PER == deployModeEnum) {
        log.info("yarn模式,不需要获取appId");
      } else {
        if (StringUtils.isEmpty(appId)) {
          localLog.append("appId无法获取").append(TipsConstants.TIPS_1);
          throw new RuntimeException("解析appId异常");
        }
      }
      JobBaseServiceAOImpl.THREADAPPID.set(appId);

      log.info("获取到的appId是 {}", appId);
      return appId;
    } catch ( BizException e) {
      throw e;
    } catch (Exception e) {
      log.error("[clearInfoLogStream] is error", e);
      throw new RuntimeException("clearInfoLogStream is error");
    } finally {
      this.close(reader, stream, "clearInfoLogStream");

    }
  }


  /**
   * 关闭流
   *
   * @author zhp
   * @date 2021/3/28
   * @time 12:53
   */
  private void close(BufferedReader reader, InputStream stream, String typeName) {
    if (reader != null) {
      try {
        reader.close();
        log.info("[{}]关闭reader ", typeName);
      } catch (IOException e) {
        log.error("[{}] 关闭reader流失败 ", typeName, e);
      }
    }
    if (stream != null) {
      try {
        log.info("[{}]关闭stream ", typeName);
        stream.close();
      } catch (IOException e) {
        log.error("[{}] 关闭stream流失败 ", typeName, e);
      }
    }
    log.info("线程池状态: {}", WaitForPoolConfig.getInstance().getThreadPoolExecutor());
  }

}
