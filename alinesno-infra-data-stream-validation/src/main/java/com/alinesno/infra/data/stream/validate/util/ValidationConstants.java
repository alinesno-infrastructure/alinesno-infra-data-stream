package com.alinesno.infra.data.stream.validate.util;

/**
 * @author zhp
 * @Description:
 * @date 2021/4/5
 * @time 10:05
 */
public class ValidationConstants {

    public static final String MESSAGE_010 = "必须包含 insert or insert overwrite 语句";

    public static final String MESSAGE_011 = "暂时不支持直接使用select语句，请使用 insert into  select 语法 或者使用 print 连接器打印结果";

    public static final String TABLE_SQL_DIALECT_1 = "table.sql-dialect";


    public static final String INSERT = "INSERT";

    public static final String SELECT = "SELECT";
    public static final String SPLIT_1 = "'";
    public static final String SPACE = "";
}
