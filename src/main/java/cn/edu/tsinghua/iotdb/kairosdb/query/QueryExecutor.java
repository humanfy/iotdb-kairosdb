package cn.edu.tsinghua.iotdb.kairosdb.query;

import cn.edu.tsinghua.iotdb.kairosdb.conf.Config;
import cn.edu.tsinghua.iotdb.kairosdb.conf.ConfigDescriptor;
import cn.edu.tsinghua.iotdb.kairosdb.dao.IoTDBUtil;
import cn.edu.tsinghua.iotdb.kairosdb.dao.MetricsManager;
import cn.edu.tsinghua.iotdb.kairosdb.query.aggregator.QueryAggregator;
import cn.edu.tsinghua.iotdb.kairosdb.query.aggregator.QueryAggregatorAlignable;
import cn.edu.tsinghua.iotdb.kairosdb.query.group_by.GroupByType;
import cn.edu.tsinghua.iotdb.kairosdb.query.result.MetricResult;
import cn.edu.tsinghua.iotdb.kairosdb.query.result.MetricValueResult;
import cn.edu.tsinghua.iotdb.kairosdb.query.result.QueryDataPoint;
import cn.edu.tsinghua.iotdb.kairosdb.query.result.QueryResult;
import cn.edu.tsinghua.iotdb.kairosdb.query.sql_builder.DeleteSqlBuilder;
import cn.edu.tsinghua.iotdb.kairosdb.query.sql_builder.QuerySqlBuilder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryExecutor {

  public static final Logger LOGGER = LoggerFactory.getLogger(QueryExecutor.class);
  private static final Config config = ConfigDescriptor.getInstance().getConfig();

  private Query query;

  private Long startTime;
  private Long endTime;

  private Map<String, Integer> tag2pos;
  private Map<Integer, String> pos2tag;

  private Map<Integer, List<String>> tmpTags;

  public QueryExecutor(Query query) {
    this.query = query;
    this.startTime = query.getStartTimestamp();
    this.endTime = query.getEndTimestamp();
  }

  public QueryResult execute() throws QueryException {
    long start = System.currentTimeMillis();
    QueryResult queryResult = new QueryResult();
    if(config.DEBUG == 2) {
      long elapse = System.currentTimeMillis() - start;
      LOGGER.info("2.1 [parse query] cost {} ms", elapse);
      start = System.currentTimeMillis();
    }
    for (QueryMetric metric : query.getQueryMetrics()) {
      long start1 = System.currentTimeMillis();
      if (getMetricMapping(metric)) {
        long start2 = System.currentTimeMillis();
        MetricResult metricResult = new MetricResult();

        String sql = buildSqlStatement(metric, pos2tag, tag2pos.size(), startTime, endTime);

        MetricValueResult metricValueResult = new MetricValueResult(metric.getName());
        if(config.DEBUG == 4) {
          long elapse = System.currentTimeMillis() - start2;
          LOGGER.info("2.2.1.1 [build SQL statement and new Result class] of metric={} cost {} ms", metric.getName(), elapse);
          start2 = System.currentTimeMillis();
        }
        metricResult.setSampleSize(getValueResult(sql, metricValueResult));
        if(config.DEBUG == 4) {
          long elapse = System.currentTimeMillis() - start2;
          LOGGER.info("2.2.1.2 [metricResult.setSampleSize(getValueResult(sql, metricValueResult))] of metric={} cost {} ms", metric.getName(), elapse);
          start2 = System.currentTimeMillis();
        }
        setTags(metricValueResult);
        if(config.DEBUG == 4) {
          long elapse = System.currentTimeMillis() - start2;
          LOGGER.info("2.2.1.3 [setTags(metricValueResult)] of metric={} cost {} ms", metric.getName(), elapse);
          start2 = System.currentTimeMillis();
        }
        if (metricResult.getSampleSize() == 0) {
          queryResult.addVoidMetricResult(metric.getName());
        } else {
          metricResult.addResult(metricValueResult);

          metricResult = doAggregations(metric, metricResult);

          queryResult.addMetricResult(metricResult);
          if(config.DEBUG == 4) {
            long elapse = System.currentTimeMillis() - start2;
            LOGGER.info("2.2.1.4 [doAggregations] of metric={} cost {} ms", metric.getName(), elapse);
          }
        }

      } else {
        queryResult.addVoidMetricResult(metric.getName());
      }
      if(config.DEBUG == 3) {
        long elapse = System.currentTimeMillis() - start1;
        LOGGER.info("2.2.1 [for (QueryMetric metric : query.getQueryMetrics())] of metric={} cost {} ms", metric.getName(), elapse);
      }
    }
    if(config.DEBUG == 2) {
      long elapse = System.currentTimeMillis() - start;
      LOGGER.info("2.2 [for (QueryMetric metric : query.getQueryMetrics())] loop cost {} ms", elapse);
    }

    return queryResult;
  }

  public void delete() {
    for (QueryMetric metric : query.getQueryMetrics()) {

      if (getMetricMapping(metric)) {
        String querySql = buildSqlStatement(metric, pos2tag, tag2pos.size(), startTime, endTime);
        List<Connection> connections = null;
        try {
          connections = IoTDBUtil.getNewConnection();
        } catch (SQLException e) {
          e.printStackTrace();
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
        for (Connection conn : connections) {
          try {
            Statement statement = conn.createStatement();
            statement.execute(querySql);

            ResultSet rs = statement.getResultSet();

            List<String> sqlList = buildDeleteSql(rs);
            statement = conn.createStatement();
            for (String sql : sqlList) {
              statement.addBatch(sql);
            }
            statement.executeBatch();

          } catch (SQLException e) {
            LOGGER.error(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
          }

        }
      }

    }
  }

  private boolean getMetricMapping(QueryMetric metric) {
    tag2pos = MetricsManager.getTagOrder(metric.getName());
    pos2tag = new HashMap<>();

    if (tag2pos == null) {
      return false;
    } else {
      for (Map.Entry<String, List<String>> tag : metric.getTags().entrySet()) {
        String tmpKey = tag.getKey();
        Integer tempPosition = tag2pos.getOrDefault(tmpKey, null);
        if (tempPosition == null) {
          return false;
        }
        pos2tag.put(tempPosition, tmpKey);
      }
    }

    return true;
  }

  private String buildSqlStatement(QueryMetric metric, Map<Integer, String> pos2tag, int maxPath,
      long startTime, long endTime) {
    QuerySqlBuilder sqlBuilder = new QuerySqlBuilder(metric.getName());

    for (int i = 0; i < maxPath; i++) {
      String tmpKey = pos2tag.getOrDefault(i, null);
      if (tmpKey == null) {
        sqlBuilder.append("*");
      } else {
        sqlBuilder.append(metric.getTags().get(tmpKey));
      }
    }

    return sqlBuilder.generateSql(startTime, endTime);
  }

  private List<String> buildDeleteSql(ResultSet rs) throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();

    String[] paths = new String[metaData.getColumnCount() - 1];
    int[] types = new int[metaData.getColumnCount() - 1];

    for (int i = 2; i <= metaData.getColumnCount(); i++) {
      paths[i - 2] = metaData.getColumnName(i);
      types[i - 2] = metaData.getColumnType(i);
    }

    DeleteSqlBuilder builder;
    builder = new DeleteSqlBuilder();

    while (rs.next()) {
      String timestamp = rs.getString(1);
      for (int i = 2; i <= metaData.getColumnCount(); i++) {
        if (rs.getString(i) != null) {
          builder.appendDataPoint(paths[i - 2], timestamp);
        }
      }
    }

    return builder.build(paths, types);
  }

  private long getValueResult(String sql, MetricValueResult metricValueResult) {
    long start = System.currentTimeMillis();
    long sampleSize = 0L;
    if (sql == null || metricValueResult == null) {
      return sampleSize;
    }
    LOGGER.info("start to execute query: {}", sql);
    Connection connection = null;
    try {
      connection = IoTDBUtil.getConnection().get(0);
    } catch (SQLException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    try (Statement statement = connection.createStatement()) {
      LOGGER.info("Send query SQL: {}", sql);
      statement.execute(sql);
      try(ResultSet rs = statement.getResultSet()) {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        int type = metaData.getColumnType(2);
        boolean[] paths = new boolean[columnCount - 1];

        long start1 = System.currentTimeMillis();
        long nextStart = start1;
        long total = 0;
        while (rs.next()) {
          total += System.currentTimeMillis() - nextStart;
          long timestamp = rs.getLong(1);
          for (int i = 2; i <= columnCount; i++) {
            String value = rs.getString(i);
            //????
            if (value == null || value.equals(DeleteSqlBuilder.NULL_STR) || value
                .equals("2.147483646E9")) {
              continue;
            }
            sampleSize++;
            paths[i - 2] = true;
            QueryDataPoint dataPoint = null;
            switch (type) {
              case Types.BIGINT:
              case Types.INTEGER:
                int intValue = rs.getInt(i);
                dataPoint = new QueryDataPoint(timestamp, intValue);
                break;
              case Types.DOUBLE:
                double doubleValue = rs.getDouble(i);
                dataPoint = new QueryDataPoint(timestamp, doubleValue);
                break;
              case Types.VARCHAR:
                dataPoint = new QueryDataPoint(timestamp, value);
                break;
              default:
                LOGGER.error("QueryExecutor.execute: invalid type");
            }
            metricValueResult.addDataPoint(dataPoint);
          }
          nextStart = System.currentTimeMillis();
        }

        if (config.DEBUG == 5) {
          long elapse = System.currentTimeMillis() - start1;
          LOGGER.info("2.2.1.2.1 while (rs.next()) loop cost {} ms, rs.next() cost {} ms", elapse, total);
        }

        getTagValueFromPaths(metaData, paths);

        addBasicGroupByToResult(type, metricValueResult);
      }
    } catch (SQLException e) {
      LOGGER.warn(String.format("QueryExecutor.%s: %s", e.getClass().getName(), e.getMessage()));
    }
    if (config.DEBUG == 5) {
      long elapse = System.currentTimeMillis() - start;
      LOGGER.info("2.2.1.2 [getValueResult()] cost {} ms", elapse);
    }
    return sampleSize;
  }

  private void getTagValueFromPaths(ResultSetMetaData metaData, boolean[] hasPaths)
      throws SQLException {
    tmpTags = new HashMap<>();
    int columnCount = metaData.getColumnCount();
    for (int i = 2; i <= columnCount; i++) {
      if (!hasPaths[i - 2]) {
        continue;
      }
      String[] paths = metaData.getColumnName(i).split("\\.");
      int pathsLen = paths.length;
      for (int j = 2; j < pathsLen - 1; j++) {
        List<String> list = tmpTags.getOrDefault(j, null);
        if (list == null) {
          list = new LinkedList<>();
          tmpTags.put(j, list);
        }
        if (!list.contains(paths[j])) {
          list.add(paths[j]);
        }
      }
    }
  }

  private void setTags(MetricValueResult metricValueResult) {
    if (tmpTags != null) {
      for (Map.Entry<String, Integer> entry : tag2pos.entrySet()) {
        pos2tag.put(entry.getValue(), entry.getKey());
      }

      for (Map.Entry<Integer, List<String>> entry : tmpTags.entrySet()) {
        metricValueResult.setTag(pos2tag.get(entry.getKey() - 2), entry.getValue());
      }
    }
  }

  private void addBasicGroupByToResult(
      int type, MetricValueResult metricValueResult) throws SQLException {
    if (type == Types.VARCHAR) {
      metricValueResult.addGroupBy(GroupByType.getTextTypeInstance());
    } else {
      metricValueResult.addGroupBy(GroupByType.getNumberTypeInstance());
    }
  }

  private MetricResult doAggregations(QueryMetric metric, MetricResult result)
      throws QueryException {

    for (QueryAggregator aggregator : metric.getAggregators()) {
      if (aggregator instanceof QueryAggregatorAlignable) {
        ((QueryAggregatorAlignable) aggregator).setStartTimestamp(startTime);
        ((QueryAggregatorAlignable) aggregator).setEndTimestamp(endTime);
      }
      result = aggregator.doAggregate(result);
    }

    return result;
  }

  private int findType(String string) {
    if (isNumeric(string)) {
      return Types.INTEGER;
    } else {
      if (string.contains(".")) {
        return Types.DOUBLE;
      } else {
        return Types.VARCHAR;
      }
    }
  }

  private boolean isNumeric(String string) {
    for (int i = 0; i < string.length(); i++) {
      if (!Character.isDigit(string.charAt(i))) {
        return false;
      }
    }
    return true;
  }

}
