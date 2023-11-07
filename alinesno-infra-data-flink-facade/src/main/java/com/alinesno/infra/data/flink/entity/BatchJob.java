package com.alinesno.infra.data.flink.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zhp
 * @Description:
 * @date 2022/10/29
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class BatchJob {

//  public BatchJob() {
//  }
//
//  public BatchJob(Long id, String jobName, String cron) {
//    this.id = id;
//    this.jobName = jobName;
//    this.cron = cron;
//  }

  private Long id;

  /**
   * 任务名称
   */
  private String jobName;

  /**
   * cron表达式
   */
  private String cron;

}
