package com.yaomalang.aidetector.timeseries;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yaomalang.aidetector.analysis.IntelligentBethune;
import com.yaomalang.aidetector.dataobject.AnomalyData;
import com.yaomalang.aidetector.tools.DateUtils;
import com.yaomalang.aidetector.tools.NumericTools;
import com.yaomalang.aidetector.tools.ThresholdConfig;
import com.enmotech.bethunepro.common.datastructure.TimingValue;
import com.enmotech.bethunepro.common.datastructure.TreeNode;
import smile.stat.distribution.KernelDensity;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description : 时间序列异常检测
 * @Author : fanwenyong
 * @Create Date : 2017/10/25 14:57
 * @ModificalHistory :
 */
public class AnomalyDetection extends TimeSeries implements IntelligentBethune<TreeNode<TimingValue>, JSONObject> {


    private TreeNode<TimingValue> root;
    private Map<String, List<TreeNode<TimingValue>>> rootDataMap;
    private JSONObject resultJSON;

    public AnomalyDetection(TreeNode<TimingValue> root) {
        this.root = root;
        init();
    }

    public void init() {
        rootDataMap = getNodeTimeSeries(root);
        TimingValue tv = root.getData();
        this.resultJSON = createJson(tv.name, tv.timestamp, 0, 1.0, 0, 0);
    }

    @Override
    public void predict() throws Exception {
        buildSeriesAndAnomalyDetection(this.rootDataMap, this.resultJSON);
    }


    @Override
    public JSONObject getResults() throws Exception {
        sort(this.resultJSON);
//        System.out.println(this.resultJSON);
        return this.resultJSON;
    }

    @Override
    public boolean isLessData() {
        if (root == null || root.getChildren().size() < ThresholdConfig.getMinAmount())
            return true;
        return false;
    }

    /**
     * 构建时间序列并检查检点异常
     *
     * @param rootDataMap
     */
    public void buildSeriesAndAnomalyDetection(Map<String, List<TreeNode<TimingValue>>> rootDataMap, JSONObject jsonObject) {

        List<Map<String, List<TreeNode<TimingValue>>>> children = new ArrayList<>();
        List<Instances> incrementalInstances = new ArrayList<>();
        List<Instances> originalInstances = new ArrayList<>();

        /**构建同一层次的Instances和聚合子节点的数据*/
        for (Map.Entry<String, List<TreeNode<TimingValue>>> entry : rootDataMap.entrySet()) {

            List<TreeNode<TimingValue>> nodes = entry.getValue();

            List<Long> times = new ArrayList<>();
            List<Long> values = new ArrayList<>();

            /**创建节点数据时间序列并聚合该节点下的所有子节点**/
            Map<String, List<TreeNode<TimingValue>>> childMap = null;
            for (int i = 0; i < nodes.size(); i++) {
                TreeNode<TimingValue> node = nodes.get(i);
                TimingValue tv = node.getData();
                times.add(tv.timestamp);
                values.add(tv.value);

                if (node.isLeaf()) continue;

                if (childMap == null) {
                    childMap = getNodeTimeSeries(node);
                } else {
                    for (TreeNode<TimingValue> leaf : node.getChildren()) {
                        put(childMap, leaf);
                    }
                }
            }

            Instances instances = buildInstances(entry.getKey(), entry.getKey(), times.stream().mapToDouble(d -> d).toArray(), values.stream().mapToDouble(d -> d).toArray());
            instances = replaceMissingValues(instances);
            originalInstances.add(instances);

            Instances difference = difference(instances);//做一阶差分
            incrementalInstances.add(difference);

            if (childMap == null) continue;

            //获取当前时间序列的最大时间
            times.sort((o1, o2) -> {
                if (o1 > o2) {
                    return 1;
                }
                return -1;
            });
            long maxTime = times.get(times.size() - 1);

            List<String> invalidKeys = new ArrayList<>();
            for (Map.Entry<String, List<TreeNode<TimingValue>>> cEntry : childMap.entrySet()) {
                //取出孩子时间序列的最大时间
                List<TreeNode<TimingValue>> nodeList = cEntry.getValue();
                nodeList.sort((o1, o2) -> {
                    if (o1.getData().timestamp > o2.getData().timestamp) {
                        return 1;
                    }
                    return -1;
                });
                //如果孩子时间序列的最大时间不等于父时间序列的最大时间或者最大时间相等但是值小于等于0，将其取出并在后面删除
                //与根节点的最大时间不符或者值为0，则认为是正常数据，不需要进行检测
                TimingValue tv = nodeList.get(nodeList.size() - 1).getData();
                if (maxTime != tv.timestamp || (tv.timestamp == maxTime && tv.value <= 0)) {
                    invalidKeys.add(cEntry.getKey());
                    continue;
                }

                if (nodeList.size() == times.size()) continue;
                //对中间缺失的部分数据，根据根节点进行补全
                for (int i = 0; i < times.size(); i++) {
                    String name = "";
                    long value = 0l;
                    int len = 0;
                    for (int j = 0; j < cEntry.getValue().size(); j++) {
                        tv = cEntry.getValue().get(j).getData();
                        name = tv.name;
                        value = tv.value;
                        if (tv.timestamp == times.get(i)) {
                            break;
                        }
                        len++;
                    }
                    if (len == cEntry.getValue().size()) {
                        cEntry.getValue().add(new TreeNode(TimingValue.of(name, times.get(i), value)));
                    }
                }
            }
            //删除最大时间
            for (String key : invalidKeys) {
                childMap.remove(key);
            }
            children.add(childMap);
        }

        /**2.检测同一级指标的异常并计算权重权重*/
        buildAnomalyTree(originalInstances, incrementalInstances, jsonObject);

        /**3.递归检测下一个节点*/
        for (int i = 0; i < children.size(); i++) {
            buildSeriesAndAnomalyDetection(children.get(i), jsonObject.getJSONArray("children").getJSONObject(i));
        }
    }

    /**
     * 创建异常树
     *
     * @param incrementalInstances 增量数据集
     */
    public void buildAnomalyTree(List<Instances> originalInstances, List<Instances> incrementalInstances, JSONObject jsonObject) {

        AnomalyData anomalyData = abnormalDetection(originalInstances, incrementalInstances);
        //同一级别的节点名称
        String[] names = anomalyData.getNames();

        //取前一个时间点的数据作为权重的计算
        double[] preUsedBytesArray = anomalyData.getPreUsedBytesArray();
        //每个时间序列的权重
        double[] weights = NumericTools.getRate(preUsedBytesArray);

        //每个时间序列的异常点(或离群点)，通过增量进行预测
        List<Map<Integer, double[]>> outliers = anomalyData.getOutlierList();

        //正常通道
        List<Instances> normalChannels = anomalyData.getNormalChannels();

        //计算每个异常点的权重
        createAbnormalNode(incrementalInstances, names, weights, outliers, normalChannels, jsonObject);
    }

    /**
     * 异常检测
     *
     * @param incrementalInstances
     * @return
     */
    public AnomalyData abnormalDetection(List<Instances> originalInstances, List<Instances> incrementalInstances) {

        String[] names = new String[incrementalInstances.size()];
        double[] preUsedBytesArray = new double[incrementalInstances.size()];
        List<Map<Integer, double[]>> outlierList = new ArrayList<>();
        List<Instances> normalChannels = new ArrayList<>();

        for (int i = 0; i < incrementalInstances.size(); i++) {
            Instances increment = incrementalInstances.get(i);
            names[i] = increment.attribute(getDataAttrIndex()).name();

            setDataField(names[i]);
            setTimeStampField(increment.attribute(getTimeAttrIndex()).name());

            Map<Integer, double[]> outlierMap;
            if (increment.numInstances() < ThresholdConfig.getMinModel()) {//数据量少于模型定义的最少数据量时，计算最后一个数据的增长率，如果增长率大于设定的阀值，则作为异常处理
                outlierMap = new HashMap<>();

                Instances origin = originalInstances.get(i);
                double originValue = origin.get(origin.numInstances() - 2).value(getDataAttrIndex());
                double incrementValue = increment.lastInstance().value(getDataAttrIndex());

                if (originValue == 0 && incrementValue > 0) {
                    outlierMap.put(increment.numInstances() - 1, new double[]{originValue, 1.0});
                } else if (incrementValue / originValue >= ThresholdConfig.getIncrementalRate()) {
                    outlierMap.put(increment.numInstances() - 1, new double[]{originValue, incrementValue / originValue});
                } else {
                    outlierMap.put(increment.numInstances() - 1, new double[]{originValue, 0});
                }
                normalChannels.add(origin);
                preUsedBytesArray[i] = origin.meanOrMode(getDataAttrIndex());

            } else {
                confirmPeriodicity(increment);

                Instances normalChannel = verticalTranslationSeasonal(increment);
                normalChannels.add(normalChannel);
                preUsedBytesArray[i] = normalChannel.meanOrMode(getDataAttrIndex());
                outlierMap = anomalyDetection(increment, normalChannel);
            }
            if (outlierMap != null)
                outlierList.add(outlierMap);
        }
        AnomalyData anomalyData = new AnomalyData(names, preUsedBytesArray, outlierList, normalChannels);

        return anomalyData;
    }

    /**
     * 创建异常节点
     *
     * @param instancesList
     * @param names
     * @param weights
     * @param outliers
     * @param normalChannels
     * @param jsonObject
     */
    public void createAbnormalNode(List<Instances> instancesList, String[] names, double[] weights, List<Map<Integer, double[]>> outliers, List<Instances> normalChannels, JSONObject jsonObject) {
        for (int i = 0; i < outliers.size(); i++) {
            Map<Integer, double[]> outlierMap = outliers.get(i);
            if (outlierMap == null) continue;
            //仅返回最有一个节点，不论是否异常
            Instances instances = instancesList.get(i);
            int lastIndex = instances.numInstances() - 1;
            long time = (long) instances.get(lastIndex).value(getTimeAttrIndex());
            //增量数据
            Instances normalChannel = normalChannels.get(i);
            double threshold = normalChannel.get(normalChannel.numInstances() - 1).value(getDataAttrIndex());
            double increment = instances.get(instances.numInstances() - 1).value(getDataAttrIndex());

            double errorLevel = 0;
            double[] outlier = outlierMap.get(lastIndex);
            if (outlier != null) {
                threshold = outlier[0];
                errorLevel = outlier[1] * weights[i];
            }
            JSONObject outlierJson = createJson(names[i], time, threshold, weights[i], errorLevel, increment);
            jsonObject.getJSONArray("children").add(outlierJson);
        }
    }

    /**
     * @param root
     * @return
     */
    private Map<String, List<TreeNode<TimingValue>>> getNodeTimeSeries(TreeNode<TimingValue> root) {
        Map<String, List<TreeNode<TimingValue>>> timeSeriesMap = new HashMap<>();
        for (TreeNode<TimingValue> node : root.getChildren()) {
            put(timeSeriesMap, node);
        }
        return timeSeriesMap;
    }

    /**
     * @param targetMap
     * @param node
     */
    private void put(Map<String, List<TreeNode<TimingValue>>> targetMap, TreeNode<TimingValue> node) {
        TimingValue tv = node.getData();
        if (targetMap.get(tv.name) == null) {
            List<TreeNode<TimingValue>> nodes = new ArrayList<>();
            nodes.add(node);
            targetMap.put(tv.name, nodes);
        } else {
            targetMap.get(tv.name).add(node);
        }
    }

    /**
     * 寻找最大概率密度对应的值
     *
     * @param values
     */
    public double findValueOfMaxKDP(double[] values) {

//        double sd = NumericTools.getStdDev(values);
//        double h = 1.06 * sd * Math.pow(values.length, -1.0 / 5);
        KernelDensity kde = new KernelDensity(values);
        Double maxKDP = null;
        int index = -1;
        for (int i = 0; i < values.length; i++) {
            double kdp = kde.p(values[i]);
            if (maxKDP == null) {
                maxKDP = kdp;
                index = i;
                continue;
            }
            if (maxKDP < kdp) {
                maxKDP = kdp;
                index = i;
            }
        }

        return values[index];
    }

    /**
     * 排序
     *
     * @param jsonObject
     */
    private void sort(JSONObject jsonObject) {
        jsonObject.getJSONArray("children").sort((o1, o2) -> {
            JSONObject jsonObject1 = (JSONObject) o1;
            JSONObject jsonObject2 = (JSONObject) o2;

            double errorLevel1 = jsonObject1.getDouble("errorLevel");
            double errorLevel2 = jsonObject2.getDouble("errorLevel");
//            double threshold1 = jsonObject1.getDouble("threshold");
//            double threshold2 = jsonObject2.getDouble("threshold");
//            double increment1 = jsonObject1.getDouble("increment");
//            double increment2 = jsonObject2.getDouble("increment");
            double weight1 = jsonObject1.getDouble("weight");
            double weight2 = jsonObject2.getDouble("weight");

//            weight1 = increment1 / threshold1 * weight1;
//            weight2 = increment2 / threshold2 * weight2;

            if (errorLevel1 == errorLevel2) {
                if (weight1 > weight2) {
                    return -1;
                } else if (weight1 == weight2) {
                    return 0;
                }
            } else if (errorLevel1 > errorLevel2) {
                return -1;
            }
            return 1;
        });
        jsonObject.getJSONArray("children").forEach(object -> {
            JSONObject json = (JSONObject) object;
            if (json.getJSONArray("children").size() > 1)
                sort((JSONObject) object);
        });
    }

    @Override
    public Instances addMissedTimeSeries(Instances instances, int scala) {
        Instances copyInstances = copy(instances);

        int count = 0;
        long standardDistance = missedTimeSeriesStatistics(instances);

        List<Instance> addList = new ArrayList<>();
        // 对缺失的时间进行补缺或者删除重复的时间
        for (int i = 0; i < instances.numInstances() - 1; i++) {
            Instance prev = instances.get(i);
            Instance next = instances.get(i + 1);
            double distance = next.value(getTimeAttrIndex()) - prev.value(getTimeAttrIndex());
            if (standardDistance == distance)
                continue;
            if (distance == 0) {
                copyInstances.delete(i);
                continue;
            }
            count = (int) (distance / standardDistance);
            for (int j = 0; j < count - 1; j++) {
                double[] values = new double[instances.numAttributes()];
                for (int k = 0; k < values.length; k++) {
                    if (k == getTimeAttrIndex()) {
                        values[k] = prev.value(getTimeAttrIndex()) + standardDistance * (j + 1);
                    } else {
                        values[k] = prev.value(getDataAttrIndex());
                    }
                }
                Instance instance = new DenseInstance(1.0, values);
                addList.add(instance);
            }
        }
        //插入缺失的时间序列
        for (Instance instance : addList) {
            copyInstances.add(instance);
        }
        copyInstances = removeDuplicates(copyInstances);
        copyInstances.sort(getTimeAttrIndex());

//        for (int i = 0; i < copyInstances.numInstances() - 1; i++) {
//            Instance prev, next;
//            if (i < instances.numInstances() - 1) {
//                prev = instances.get(i);
//                next = instances.get(i + 1);
//            } else {
//                prev = copyInstances.get(i);
//                next = copyInstances.get(i + 1);
//            }
//            if (prev.value(getDataAttrIndex()) > 0 && next.value(getDataAttrIndex()) <= 0) {
//                copyInstances.get(i + 1).setValue(getDataAttrIndex(), prev.value(getDataAttrIndex()));
//            }
//        }

        return copyInstances;
    }

    @Override
    public void repairAnomalyValue(int scala, Instances copyInstances) {
        copyInstances.forEach(instance -> {
            if (instance.value(getDataAttrIndex()) < 0) {
                instance.setValue(getDataAttrIndex(), 0);
            }
        });
    }

    /**
     * @param name       节点名称
     * @param weight     节点的权重
     * @param timestamp  时间点
     * @param threshold  该时间点的正常通道值
     * @param errorLevel 该时间点的异常级别, 如果是0表示该该点正常
     * @param increment  增量
     * @return
     */
    protected JSONObject createJson(String name, long timestamp, double threshold, double weight, double errorLevel, double increment) {
        JSONObject json = new JSONObject(true);
        json.put("name", name);
        json.put("weight", weight);
        json.put("timestamp", DateUtils.parserToString(timestamp, DateUtils.PATTERN_SECOND_1));
        json.put("threshold", threshold);
        json.put("errorLevel", (Double.isNaN(errorLevel) || Double.isInfinite(errorLevel)) ? 0.0 : errorLevel);
        json.put("increment", (Double.isNaN(increment) || Double.isInfinite(increment)) ? 0.0 : increment);
        json.put("children", new JSONArray());
        return json;
    }
}
