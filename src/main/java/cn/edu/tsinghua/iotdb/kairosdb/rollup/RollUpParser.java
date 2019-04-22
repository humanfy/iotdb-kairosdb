package cn.edu.tsinghua.iotdb.kairosdb.rollup;

import cn.edu.tsinghua.iotdb.kairosdb.conf.Config;
import cn.edu.tsinghua.iotdb.kairosdb.conf.ConfigDescriptor;
import cn.edu.tsinghua.iotdb.kairosdb.datastore.TimeUnit;
import cn.edu.tsinghua.iotdb.kairosdb.http.rest.json.TimeUnitDeserializer;
import cn.edu.tsinghua.iotdb.kairosdb.query.QueryMetric;
import cn.edu.tsinghua.iotdb.kairosdb.query.aggregator.QueryAggregator;
import cn.edu.tsinghua.iotdb.kairosdb.query.aggregator.QueryAggregatorDeserializer;
import cn.edu.tsinghua.iotdb.kairosdb.query.group_by.GroupBy;
import cn.edu.tsinghua.iotdb.kairosdb.query.group_by.GroupByDeserializer;
import cn.edu.tsinghua.iotdb.kairosdb.query.group_by.GroupBySerializer;
import cn.edu.tsinghua.iotdb.kairosdb.query.result.QueryDataPoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RollUpParser {

  private Gson gson;
  private static final Config config = ConfigDescriptor.getInstance().getConfig();

  public RollUpParser() {
    gson = new GsonBuilder()
        .registerTypeAdapter(QueryMetric.class, new QueryMetric())
        .registerTypeAdapter(GroupBy.class, new GroupByDeserializer())
        .registerTypeAdapter(GroupBy.class, new GroupBySerializer())
        .registerTypeAdapter(QueryAggregator.class, new QueryAggregatorDeserializer())
        .registerTypeAdapter(TimeUnit.class, new TimeUnitDeserializer())
        .registerTypeAdapter(QueryDataPoint.class, new QueryDataPoint())
        .create();
  }

  public Gson getGson() {
    return gson;
  }

  public RollUp parseRollupTask(String json) throws RollUpException {
    RollUp rollUp = gson.fromJson(json, RollUp.class);
    rollUp.setId(String.valueOf(System.currentTimeMillis()));
    RollUpsExecutor.getInstance().create(rollUp);
    return rollUp;
  }


}
