package cn.edu.tsinghua.iotdb.kairosdb.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigDescriptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigDescriptor.class);

  private Config config;

  private ConfigDescriptor() {
    config = new Config();
    loadProps();
    new updateConfigThread().start();
  }

  public static ConfigDescriptor getInstance() {
    return ConfigDescriptorHolder.INSTANCE;
  }

  public Config getConfig() {
    return config;
  }

  private void loadProps() {
    String url = System.getProperty(Constants.REST_CONF, null);
    if (url != null) {
      InputStream inputStream;
      try {
        inputStream = new FileInputStream(new File(url));
      } catch (FileNotFoundException e) {
        LOGGER.warn("Fail to find config file {}", url);
        return;
      }
      Properties properties = new Properties();
      try {
        properties.load(inputStream);
        String urlList = properties.getProperty("IoTDB_LIST", "127.0.0.1:6667");
        String timeVertexListStr = properties.getProperty("TIME_DIMENSION_SPLIT",
            "2018-9-20T00:00:00+08:00,2018-10-20T00:00:00+08:00");
        String readOnlyListStr = properties.getProperty("IoTDB_READ_ONLY_LIST", "127.0.0.1:6667");
        if(!timeVertexListStr.equals("")) {
          for (String vertex : timeVertexListStr.split(",")) {
            DateTime dateTime = new DateTime(vertex);
            config.TIME_DIMENSION_SPLIT.add(dateTime.getMillis());
          }
          Collections.sort(config.TIME_DIMENSION_SPLIT);
        }
        if(!readOnlyListStr.equals("")) {
          for (String vertex : readOnlyListStr.split(";")) {
            List<String> readOnlyUrls = new ArrayList<>();
            Collections.addAll(readOnlyUrls, vertex.split(","));
            config.IoTDB_READ_ONLY_LIST.add(readOnlyUrls);
          }
        }
        Collections.addAll(config.IoTDB_LIST, urlList.split(","));
        config.REST_PORT = properties.getProperty("REST_PORT", "localhost");
        config.AGG_FUNCTION = properties.getProperty("AGG_FUNCTION", "AGG_FUNCTION");
        config.SPECIAL_TAG = properties.getProperty("SPECIAL_TAG", "SPECIAL_TAG");
        config.STORAGE_GROUP_SIZE = Integer
            .parseInt(properties.getProperty("STORAGE_GROUP_SIZE", "50"));
        config.POINT_EDGE = Integer.parseInt(properties.getProperty("POINT_EDGE", "50000000"));
        config.TIME_EDGE = Integer.parseInt(properties.getProperty("TIME_EDGE", "50000000"));
        config.MAX_ROLLUP = Integer
            .parseInt(properties.getProperty("MAX_ROLLUP", config.MAX_ROLLUP + ""));
        config.DEBUG = Integer.parseInt(properties.getProperty("DEBUG", config.DEBUG + ""));
        config.CONNECTION_NUM = Integer
            .parseInt(properties.getProperty("CONNECTION_NUM", config.CONNECTION_NUM + ""));
        config.GROUP_BY_UNIT = Integer
            .parseInt(properties.getProperty("GROUP_BY_UNIT", config.GROUP_BY_UNIT + ""));
        config.MAX_RANGE = Long
            .parseLong(properties.getProperty("MAX_RANGE", config.MAX_RANGE + ""));
        config.LATEST_TIME_RANGE = Long
            .parseLong(properties.getProperty("LATEST_TIME_RANGE", config.LATEST_TIME_RANGE + ""));
        config.PROFILE_INTERVAL = Integer
            .parseInt(properties.getProperty("PROFILE_INTERVAL", config.PROFILE_INTERVAL + ""));
        config.CORE_POOL_SIZE = Integer
            .parseInt(properties.getProperty("CORE_POOL_SIZE", config.CORE_POOL_SIZE + ""));
        config.MAX_POOL_SIZE = Integer
            .parseInt(properties.getProperty("MAX_POOL_SIZE", config.MAX_POOL_SIZE + ""));
        config.ENABLE_PROFILER = Boolean.parseBoolean(properties.getProperty("ENABLE_PROFILER",
            config.ENABLE_PROFILER + ""));

        config.PROTOCAL_NUM = Integer.parseInt(properties.getProperty("PROTOCAL_NUM", "12"));
        List<List<String>> protocal_machine = new ArrayList<>();
        for (int i = 1; i <= config.PROTOCAL_NUM; i++) {
          List<String> machines = new ArrayList<>();
          String machine_list = properties.getProperty("PROTOCAL_" + i, "");
          Collections.addAll(machines, machine_list.split(","));
          protocal_machine.add(machines);
        }
        config.PROTOCAL_MACHINE = protocal_machine;
        config.STORAGE_GROUP_SIZE = Integer
            .parseInt(properties.getProperty("STORAGE_GROUP_SIZE", "50"));
        config.MAX_ROLLUP = Integer
            .parseInt(properties.getProperty("MAX_ROLLUP", config.MAX_ROLLUP + ""));
        config.DEBUG = Integer.parseInt(properties.getProperty("DEBUG", config.DEBUG + ""));
        config.CONNECTION_NUM = Integer
            .parseInt(properties.getProperty("CONNECTION_NUM", config.CONNECTION_NUM + ""));

      } catch (IOException e) {
        LOGGER.error("load properties error: ", e);
      }
      try {
        inputStream.close();
      } catch (IOException e) {
        LOGGER.error("Fail to close config file input stream", e);
      }
    } else {
      LOGGER.warn("{} No config file path, use default config", Constants.CONSOLE_PREFIX);
    }
  }

  private static class ConfigDescriptorHolder {

    private static final ConfigDescriptor INSTANCE = new ConfigDescriptor();
  }


  private void updateProps() {
    String url = System.getProperty(Constants.REST_CONF, null);
    if (url != null) {
      InputStream inputStream;
      try {
        inputStream = new FileInputStream(new File(url));
      } catch (FileNotFoundException e) {
        LOGGER.warn("Fail to find config file {}", url);
        return;
      }
      Properties properties = new Properties();
      try {
        properties.load(inputStream);
        config.PROTOCAL_NUM = Integer.parseInt(properties.getProperty("PROTOCAL_NUM", "12"));
        List<List<String>> protocal_machine = new ArrayList<>();
        for (int i = 1; i <= config.PROTOCAL_NUM; i++) {
          List<String> machines = new ArrayList<>();
          String machine_list = properties.getProperty("PROTOCAL_" + i, "");
          Collections.addAll(machines, machine_list.split(","));
          protocal_machine.add(machines);
        }
        config.PROTOCAL_MACHINE = protocal_machine;
      } catch (IOException e) {
        LOGGER.error("load properties error: ", e);
      }
      try {
        inputStream.close();
      } catch (IOException e) {
        LOGGER.error("Fail to close config file input stream", e);
      }
    } else {
      LOGGER.warn("{} No config file path, use default config", Constants.CONSOLE_PREFIX);
    }
  }

  /**
   * 定时更新属性的线程
   */
  private class updateConfigThread extends Thread {

    //每30分钟更新一次config文件
    public void run() {
      while (true) {
        updateProps();
        try {
          LOGGER.info("定时更新了配置");
          Thread.sleep(1800000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

}
