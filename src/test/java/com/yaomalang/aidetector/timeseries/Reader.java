package com.yaomalang.aidetector.timeseries;

import com.yaomalang.aidetector.tools.DateUtils;
import com.enmotech.bethunepro.common.datastructure.TimingValue;
import com.enmotech.bethunepro.common.datastructure.TreeNode;
import com.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Create Date : 2017/10/25 15:05
 * @ModificalHistory :
 */
public class Reader {

    /**
     * 从CSV文件中读入数据
     *
     * @return
     * @throws IOException
     */
    public static Map<String, List<Object>> readFromCSV() throws IOException {

        CSVReader reader = loadCSV("data/PD_DB_AWR_DBTIME.csv");

        String[] fields = null;
        String[] values;

        Map<String, List<Object>> dataMap = new HashMap<>();

        while ((values = reader.readNext()) != null) {

            if (fields == null || fields.length <= 0) {
                fields = values;
                for (int i = 0; i < fields.length; i++) {
                    dataMap.put(fields[i], new ArrayList<>());
                }
                continue;
            }

            if ("jydqdb2".equals(values[1])) continue;

            for (int i = 0; i < fields.length; i++) {
                dataMap.get(fields[i]).add(values[i]);
            }
        }
        return dataMap;
    }

    private static CSVReader loadCSV(String filename) throws FileNotFoundException {
        File file = new File("data");
        String path = String.format("%s/%s", file.getPath(), filename);
        return new CSVReader(new FileReader(path));
    }

    /**
     * @return
     * @throws Exception
     */
    public static Instances getInstances() throws Exception {

        Map<String, List<Object>> dataMap = readFromCSV();

        String[] snapTimes = dataMap.get("SNAP_TIME").toArray(new String[]{});
        double[] values = dataMap.get("DB_CPU").stream().mapToDouble(Reader::applyAsDouble).toArray();

        Attribute tAttribute = new Attribute("SNAP_TIME", DateUtils.PATTERN_SECOND_1);
        Attribute vAttribute = new Attribute("DB_CPU");

        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(tAttribute);
        attributes.add(vAttribute);

        Instances instances = new Instances("test", attributes, 1);
        for (int i = 0; i < snapTimes.length; i++) {
            long time = DateUtils.timeToLong(snapTimes[i], DateUtils.PATTERN_SECOND_1);
            Instance instance = new DenseInstance(1.0, new double[]{time, values[i]});
            instances.add(instance);
        }
        instances.sort(0);

        return instances;
    }

    /**
     * @throws IOException
     */
    public static TreeNode<TimingValue> readFromCSV0() throws IOException {

        CSVReader reader = loadCSV("newAbnormal_1.csv");
        String timestamp = DateUtils.getLastDate(DateUtils.PATTERN_DAY_2);
        long lastDate = DateUtils.timeToLong(timestamp, DateUtils.PATTERN_DAY_2);
        TreeNode<TimingValue> root = new TreeNode<TimingValue>(TimingValue.of("系统测试", lastDate, 0));

        String[] values;
        while ((values = reader.readNext()) != null) {
            String dName = values[1], tName = values[2], sName = "".equals(values[3]) ? " " : values[3];
            long time = DateUtils.timeToLong(values[4], DateUtils.PATTERN_SECOND_1);
            long tValue = (long) Double.parseDouble(StringUtils.isEmpty(values[5]) ? "0" : values[5]);
            long sValue = (long) Double.parseDouble(StringUtils.isEmpty(values[6]) ? "0" : values[6]);

            TreeNode<TimingValue> database = new TreeNode(TimingValue.of(dName, time, 0));
            TreeNode<TimingValue> tablespace = new TreeNode(TimingValue.of(tName, time, tValue));
            TreeNode<TimingValue> segment = new TreeNode(TimingValue.of(sName, time, sValue));

            if (root.getChild(database.getData().identity()) == null) {
                tablespace.addChild(segment);
                database.addChild(tablespace);
                root.addChild(database);
                root.addChild(database);
            } else {
                String databaseKey = database.getData().identity();
                String tableKey = tablespace.getData().identity();
                database = root.getChild(databaseKey);
                if (database.getChild(tableKey) == null) {
                    tablespace.addChild(segment);
                    root.getChild(databaseKey).addChild(tablespace);
                    continue;
                }
                database.getChild(tableKey).addChild(segment);
            }
        }

        return root;
    }

    public static double applyAsDouble(Object b) {
        return Double.valueOf(b.toString());
    }


    /**
     *
     * @return
     */
    public static Map<String, List<Object>> loadProdData(String filename) {
        Map<String, List<Object>> prodDataMap = new HashMap<>();
        prodDataMap.put("DATE_TIME", new ArrayList<>());
        prodDataMap.put("VALUE", new ArrayList<>());
        try {
            CSVReader loadCSV = loadCSV(filename);
            List<String[]> lines = loadCSV.readAll();
            lines.forEach(line -> {
                prodDataMap.get("DATE_TIME").add(line[0]);
                prodDataMap.get("VALUE").add(line[1]);
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return prodDataMap;
    }

    public static List<Double> thresholds = new ArrayList<>();

    /**
     * @return
     */
    public static List<Instances> getInstancesList() {

        List<Instances> instancesList = new ArrayList<>();

        try {
            ClassLoader loader = TimeSeriesForecastTest.class.getClassLoader();
            URL resource = loader.getResource("data");

            String path = resource.getPath();
            File file = new File(path);
            File[] files = file.listFiles(pathname -> {
                if (pathname.isFile() && pathname.getName().endsWith(".arff"))
                    return true;
                return false;
            });
            for (File f : files) {
                String name = f.getName();
                double threshold = Double.parseDouble(name.substring(name.lastIndexOf("_")+1, name.indexOf(".")));
                thresholds.add(threshold);
                FileReader reader = new FileReader(f);
                Instances instances = new Instances(reader);
                instancesList.add(instances);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return instancesList;
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<Object>> dataMap = Reader.readFromCSV();

        String[] snapTimes = dataMap.get("SNAP_TIME").toArray(new String[]{});
        double[] values = dataMap.get("DB_TIME").stream().mapToDouble(Reader::applyAsDouble).toArray();

        System.out.println();

    }
}
