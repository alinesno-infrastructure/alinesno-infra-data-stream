package com.alinesno.infra.data.stream.exchange.common;

import com.alinesno.infra.data.stream.commom.constant.SystemConstant;
import com.alinesno.infra.data.stream.exchange.common.util.Md5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.tools.jline_embedded.internal.Log;

/**
 * @author zhp
 * @Description:
 * @date 2020-09-18
 * @time 23:55
 */
public class FlinkYarnRestUriConstants {

  // 日志记录
  private static final Logger log = LoggerFactory.getLogger(Md5Utils.class);

  public static final String URI_PROXY = "proxy/";

  public static final String URI_JOBS = "jobs";

  public static final String URI_YARN_CANCEL = "/yarn-cancel";


  public static final String URI_YARN_CHECKPOINT = "/checkpoints";

  public static final String URI_YARN_INFO = "ws/v1/cluster/info";

  public static final String URI_YARN_OVERVIEW = "#/overview";

  public static final String URI_YARN_JOB_OVERVIEW = "#/job/%s/overview";

  public static final String URI_CHECKPOINTS_JOB = "jobs/%s/checkpoints";


  public static String getUriJobsForYarn(String appId) {
    return rootUriForYarn(appId) + URI_JOBS;
  }

  public static String getUriOverviewForYarn(String appId) {
    return rootUriForYarn(appId) + URI_YARN_OVERVIEW;
  }

  public static String getUriJobsForStandalone(String appId) {
    return URI_JOBS + SystemConstant.VIRGULE  + appId;
  }


  public static String getUriCancelForYarn(String appId, String jobId) {
    return getUriJobsForYarn(appId) + SystemConstant.VIRGULE  + jobId + URI_YARN_CANCEL;
  }

  public static String getUriCancelForStandalone(String jobId) {
    return URI_JOBS + SystemConstant.VIRGULE  + jobId + URI_YARN_CANCEL;
  }

  public static String getUriCheckpointForYarn(String appId, String jobId) {
    return getUriJobsForYarn(appId) + SystemConstant.VIRGULE  + jobId + URI_YARN_CHECKPOINT;
  }

  public static String rootUriForYarn(String appId) {
    return String.format(URI_PROXY + "%s/", appId);
  }

  public static String getUriCheckpoints(String appId) {
    return String.format(URI_CHECKPOINTS_JOB, appId);
  }


  public static void main(String[] args) {
    Log.debug(" {}",FlinkYarnRestUriConstants.getUriJobsForYarn("xxxx"));
    Log.debug(" {}",FlinkYarnRestUriConstants.getUriCheckpoints("xxxx"));
  }

}
