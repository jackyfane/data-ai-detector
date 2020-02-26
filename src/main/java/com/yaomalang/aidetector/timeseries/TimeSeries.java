package com.yaomalang.aidetector.timeseries;

import com.yaomalang.aidetector.analysis.AbstractAI;
import com.yaomalang.aidetector.tools.DateUtils;
import com.yaomalang.aidetector.tools.NumericTools;
import com.yaomalang.aidetector.tools.ThresholdConfig;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.timeseries.WekaForecaster;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

/**
 * 世界序列抽象类，实现时间序列基本功能
 *
 * @author fanwenyong
 * @time
 */
public abstract class TimeSeries extends AbstractAI {

    public enum Units {
        SECONDLY, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, UNKNOWN
    }


    public final static String TIME_ATTRIBUTE = "DATE_TIME"; //默认的时间属性字段名称

    private String timeStampField; //时间属性名称
    private int timeAttrIndex = -1; //时间属性索引
    private String format;//时间属性时间格式

    private String dataField;
    private int dataAttrIndex = -1;

    private int steps;
    private int period = 12;
    private int days = 0;
    private long tolerance;

    private Units unit = Units.UNKNOWN;


    /**
     * 构造WEKA实例数据集
     *
     * @return
     * @throws Exception
     */
    public Instances createInstances(Map<String, List<Object>> dataMap) throws Exception {

        Set<String> keys = dataMap.keySet();
        List<String> attributes = new ArrayList<>();
        int attrNum = keys.size();
        String[][] dataSets = null;
        int i = 0;
        for (String key : keys) {
            List<Object> objectList = dataMap.get(key);
            if (dataSets == null) {
                dataSets = new String[attrNum][objectList.size()];
            }
            for (int j = 0; j < objectList.size(); j++) {
                dataSets[i][j] = String.valueOf(objectList.get(j));
            }
            attributes.add(key);
            i++;
        }

        Instances instances = buildInstances("bethune", attributes, dataSets);

        return instances;
    }

    /**
     * 构建WEKA实例集
     *
     * @param relation
     * @param attribute
     * @param timeSeries
     * @param dataSeries
     * @return
     */
    public Instances buildInstances(String relation, String attribute, String[] timeSeries, double[] dataSeries) {

        String dateTimeFormat = DateUtils.getDateFormat(timeSeries[0]);
        double[] times = new double[timeSeries.length];
        for (int i = 0; i < timeSeries.length; i++) {
            Double timestamp = (double) DateUtils.timeToLong(timeSeries[i], dateTimeFormat);
            times[i] = timestamp;
        }
        Instances instances = buildInstances(relation, attribute, times, dataSeries);

        return instances;
    }

    /**
     * @param relation
     * @param attribute
     * @param timeSeries
     * @param dataSeries
     * @return
     */
    public Instances buildInstances(String relation, String attribute, double[] timeSeries, double[] dataSeries) {

        Object[][] dataSets = new Object[2][timeSeries.length];
        for (int i = 0; i < timeSeries.length; i++) {
            dataSets[0][i] = DateUtils.parserToString((long) timeSeries[i], DateUtils.PATTERN_SECOND_1);
            dataSets[1][i] = dataSeries[i];
        }
        Instances instances = null;
        try {
            instances = buildInstances(relation, new String[]{TIME_ATTRIBUTE, attribute}, dataSets);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return instances;
    }

    @Override
    public Instances buildInstances(String relation, ArrayList<Attribute> arrayList, Object[][] dataSets) throws Exception {
        Instances instances = super.buildInstances(relation, arrayList, dataSets);
        // Remove the attribute that's type is not numeric or date
        String rangeList = "";
        for (int i = 0; i < instances.numAttributes(); i++) {
            Attribute attribute = instances.attribute(i);
            if (attribute.isNominal() || attribute.isString()) {
                rangeList += (i + 1) + ",";
            }
        }
        if (!"".equals(rangeList) && rangeList != null) {
            Remove filter = new Remove();
            filter.setAttributeIndices(rangeList.substring(0, rangeList.lastIndexOf(",")));
            filter.setInputFormat(instances);
            instances = Filter.useFilter(instances, filter);
        }
        confirmAttributeInfo(instances.get(0));
        instances.sort(getTimeAttrIndex());
        return instances;
    }

    /**
     * 将多个包含多个属性的时间序列拆分为单个属性的时间序列
     *
     * @param
     * @return
     */
    public List<Instances> splitToSingleAttributeSeries(Instances instances) {

        double[] timeSeries = instances.attributeToDoubleArray(getTimeAttrIndex());
        Attribute timeAttribute = instances.attribute(getTimeAttrIndex());

        List<Instances> instancesList = new ArrayList<>();
        if (instances.numAttributes() > 2) {
            for (int i = 0; i < instances.numAttributes(); i++) {
                if (getTimeAttrIndex() == i) continue;
                ArrayList<Attribute> attributes = new ArrayList<>();
                attributes.add(timeAttribute);
                attributes.add(instances.attribute(i));
                Instances saInstances = new Instances(instances.attribute(i).name(), attributes, 0);
                double[] values = instances.attributeToDoubleArray(i);
                boolean isOver = invalidDataOverPercent(values, 0.2);
                if (isOver) continue;
                for (int j = 0; j < timeSeries.length; j++) {
                    Instance instance = new DenseInstance(1.0, new double[]{timeSeries[j], values[j]});
                    saInstances.add(instance);
                }
                saInstances = replaceMissingValues(saInstances);
                instancesList.add(saInstances);
            }
        } else {
            instances = replaceMissingValues(instances);
            instancesList.add(instances);
        }

        return instancesList;
    }

    /**
     * 检查无效数据是否超过指定的比例
     *
     * @param values
     * @param percent
     * @return
     */
    public boolean invalidDataOverPercent(double[] values, double percent) {
        //针对本次需求，如果对于某一项指标的数据数据超过20%没有数据，则取消对这项指标的评估
        int invalidCnt = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0)
                invalidCnt++;
        }

        if ((double) invalidCnt / values.length > percent)
            return true;

        return false;
    }


    /**
     * 训练建模并预测
     *
     * @return
     * @throws Exception
     */

    protected WekaForecaster getWekaForecaster(Instances trainInstances) throws Exception {

        WekaForecaster forecaster = new WekaForecaster();
        GaussianProcesses classifier = new GaussianProcesses();
        classifier.setNoise(3.0);
        forecaster.setBaseForecaster(classifier);
        forecaster.getTSLagMaker().setAddQuarterOfYear(true);
        forecaster.getTSLagMaker().setAddMonthOfYear(true);
        forecaster.getTSLagMaker().setTimeStampField(getTimeStampField());
        forecaster.getTSLagMaker().setMinLag(1);
        forecaster.getTSLagMaker().setMaxLag(this.steps > 0 ? this.steps : 1);
        forecaster.setFieldsToForecast(getDataField());
        forecaster.buildForecaster(trainInstances);
        forecaster.primeForecaster(trainInstances);

        return forecaster;
    }


    /**
     * 通过少量数据调用预测接口，获取时间序列的周期名称
     * 然后通过周期名称和时间差计算周期
     *
     * @param
     * @return
     */
    protected void computePeriodicity(Instances instances) throws Exception {

        int trainSize = (int) Math.round(instances.numInstances() * 0.8);
        int testSize = instances.numInstances() - trainSize;
        Instances testInstances = new Instances(instances, trainSize, testSize);
        WekaForecaster forecaster = getWekaForecaster(testInstances);
        String periodicity = forecaster.getTSLagMaker().getPeriodicity().name();
        String start = instances.get(instances.numInstances() - 2).stringValue(getTimeAttrIndex());
        String end = instances.get(instances.numInstances() - 1).stringValue(getTimeAttrIndex());

        switch (periodicity) {
            case "HOURLY":
                this.period = 24;
                this.unit = Units.HOURLY;
                break;
            case "DAILY":
                this.period = 30;
                this.unit = Units.DAILY;
                break;
            case "WEEKLY":
                this.period = 7;
                this.unit = Units.WEEKLY;
                break;
            case "MONTHLY":
                this.period = 12;
                this.unit = Units.MONTHLY;
                break;
            case "QUARTERLY":
                this.period = 4;
                this.unit = Units.QUARTERLY;
                break;
            case "YEARLY":
                this.period = 1;
                this.unit = Units.YEARLY;
                break;
            default:
                String startSuffix = start.substring(start.length() - 2);
                String endSuffix = end.substring(end.length() - 2);
                if (getFormat().length() == 20 || getFormat().length() == 15) {
                    if (startSuffix.equals(endSuffix)) {
                        this.period = 60;
                        this.unit = Units.MINUTELY;
                    } else {
                        this.period = 60;
                        this.unit = Units.SECONDLY;
                    }
                } else if (getFormat().length() == 17 || getFormat().length() == 13) {
                    if (startSuffix.equals(endSuffix)) {
                        this.period = 24;
                        this.unit = Units.HOURLY;
                    } else {
                        this.period = 60;
                        this.unit = Units.MINUTELY;
                    }
                }
                break;
        }
        double t1 = instances.get(0).value(getTimeAttrIndex());
        double t2 = instances.get(1).value(getTimeAttrIndex());
        setTolerance((long) (t2 - t1));

        if (this.steps <= 0)
            this.steps = this.period;
    }

    /**
     * 周期计算和单位确定
     *
     * @param instances
     */
    protected void confirmPeriodicity(Instances instances) {
        double t1 = instances.get(0).value(getTimeAttrIndex());
        double t2 = instances.get(1).value(getTimeAttrIndex());
        long tolerance = (long) (t2 - t1);
        int period = 0;
        Units unit = Units.UNKNOWN;

        if (tolerance == 1000) { //秒
            period = 60;
            unit = Units.SECONDLY;
        } else if (tolerance == 1000 * 60) {//分钟
            period = 60;
            unit = Units.MINUTELY;
        } else if (tolerance == 1000 * 60 * 60) {//小时
            period = 24;
            unit = Units.HOURLY;
        } else if (tolerance == 1000 * 60 * 60 * 24) {//天
            period = 30;
            unit = Units.DAILY;
        } else if (tolerance == 1000 * 60 * 60 * 24 * 7) {//周
            period = 7;
            unit = Units.WEEKLY;
        } else if (tolerance / (24 * 60 * 60 * 1000) == 28 || tolerance / (24 * 60 * 60 * 1000) == 29 || tolerance / (24 * 60 * 60 * 1000) == 30 || tolerance / (24 * 60 * 60 * 1000) == 31) {
            period = 12;
            unit = Units.MONTHLY;
        } else if (tolerance / (24 * 60 * 60 * 1000) == 90 || tolerance / (24 * 60 * 60 * 1000) == 91 || tolerance / (24 * 60 * 60 * 1000) == 92) {//季度
            period = 4;
            unit = Units.QUARTERLY;
        } else { //年
            period = 1;
            unit = Units.YEARLY;
        }
//        if (instances.numInstances() < period && unit == Units.DAILY) {
//            period = 7;
//        }
        setPeriodicity(period, unit);
        setTolerance(tolerance);

        if (this.steps <= 0) {
            this.steps = period;
        }

    }

    /**
     * @param period
     * @param unit
     */
    private void setPeriodicity(int period, Units unit) {
        setPeriod(period);
        setUnit(unit);
    }

    /**
     * 设置时间序列时间属性信息
     *
     * @param instance
     */
    protected void confirmAttributeInfo(Instance instance) {
        for (int i = 0; i < instance.numAttributes(); i++) {
            Attribute attribute = instance.attribute(i);
            if (attribute.isDate()) {
                this.setTimeAttrIndex(i);
                this.setTimeStampField(attribute.name());
                this.setFormat(attribute.getDateFormat());
            } else {
                this.setDataAttrIndex(i);
                this.setDataField(attribute.name());
            }
        }
    }

    /**
     * 缺失补全，首先确保Instances是按照时间属性按照升序排序的
     * 1、时间缺失：时间缺失检查与补全,统计相邻时间序列的距离，把统计量最大的距离作为基准距离标准化其他距离，
     * 根据缺失个数求两个相邻数据的平均值，使用前一个时间点的值+平局值补全下个缺失的时间点的值
     * 2、异常值修复：这里的异常值是根据当前业务数据确定的，-1表示异常数据
     *
     * @param instances
     */
    @Override
    public Instances replaceMissingValues(Instances instances) {
        int scala = getScala(instances);
        Instances newInstances = addMissedTimeSeries(instances, scala);
        repairAnomalyValue(scala, newInstances);

        return newInstances;
    }


    /**
     * 确定原始数据小数点个数
     *
     * @param instances
     * @return
     */
    public int getScala(Instances instances) {
        //包含小数点个数
        int scala = 0;
        DecimalFormat decimalFormat = new DecimalFormat();
        String format = decimalFormat.format(instances.get(0).value(getDataAttrIndex()));
        if (format.contains(".")) {
            scala = format.length() - format.indexOf(".") - 1;
        }
        return scala;
    }

    /**
     * 缺失时间序列统计
     *
     * @param instances
     * @return
     */
    public long missedTimeSeriesStatistics(Instances instances) {
        Map<Long, Integer> missedTSCountMap = new HashMap();
        for (int i = 0; i < instances.numInstances() - 1; i++) {
            Instance prev = instances.get(i);
            Instance next = instances.get(i + 1);
            long distance = (long) (next.value(getTimeAttrIndex()) - prev.value(getTimeAttrIndex()));
            if (missedTSCountMap.get(distance) != null) {
                missedTSCountMap.put(distance, missedTSCountMap.get(distance) + 1);
            } else {
                missedTSCountMap.put(distance, 1);
            }
        }

        int count = -1;
        long standardDistance = -1;
        for (Map.Entry<Long, Integer> entry : missedTSCountMap.entrySet()) {
            if (count == -1) {
                count = entry.getValue();
                standardDistance = entry.getKey();
                continue;
            }
            if (count < entry.getValue()) {
                count = entry.getValue();
                standardDistance = entry.getKey();
            }
        }

        return standardDistance;
    }

    /**
     * 补全丢失的时间序列
     *
     * @param instances
     * @param scala
     * @return
     */
    public Instances addMissedTimeSeries(Instances instances, int scala) {
        Instances copyInstances = copy(instances);

        long standardDistance = missedTimeSeriesStatistics(instances);
        int count = 0;
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
            double prevValue = prev.value(getDataAttrIndex());
            double increase = (next.value(getDataAttrIndex()) - prevValue) / count; //Math.abs(nextValue - prevValue)/count;
            for (int j = 0; j < count - 1; j++) {
                prevValue += increase;
                double[] values = new double[instances.numAttributes()];
                values[getTimeAttrIndex()] = prev.value(getTimeAttrIndex()) + standardDistance * (j + 1);
                if (scala == 0) {
                    values[getDataAttrIndex()] = (long) prevValue;
                } else {
                    BigDecimal bigDecimal = new BigDecimal(prevValue);
                    bigDecimal = bigDecimal.setScale(scala);
                    values[getDataAttrIndex()] = bigDecimal.doubleValue();
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

        return copyInstances;
    }

    /**
     * 异常值处理
     *
     * @param scala
     * @param copyInstances
     */
    public void repairAnomalyValue(int scala, Instances copyInstances) {
        int startIndex = -1;
        boolean isMiss = false;
        for (int i = 0; i < copyInstances.numInstances(); i++) {
            double value = copyInstances.get(i).value(getDataAttrIndex());
            if (value == -1 && startIndex == -1) {
                startIndex = i;
                isMiss = true;
            }
            if (value > -1 && isMiss) {
                int count = i - startIndex + 1;
                double start = startIndex == 0 ? 0 : copyInstances.get(startIndex - 1).value(getDataAttrIndex());
                double mean = (value - start) / count;
                for (int j = startIndex; j < i; j++) {
                    double val;
                    if (startIndex == 0) {//如果是开始位置为异常数据，则从正常数据开始递减
                        val = copyInstances.get(i - j).value(getDataAttrIndex()) - mean;
                        if (scala == 0) {
                            copyInstances.get(i - j - 1).setValue(getDataAttrIndex(), (long) val);
                        } else {
                            BigDecimal bigDecimal = new BigDecimal(val);
                            bigDecimal = bigDecimal.setScale(scala);
                            copyInstances.get(i - j - 1).setValue(getDataAttrIndex(), bigDecimal.doubleValue());
                        }
                    } else {//如果不是是开始位置为异常数据，则从异常的前一个数开始递增
                        val = mean + copyInstances.get(j - 1).value(getDataAttrIndex());
                        if (scala == 0) {
                            copyInstances.get(j).setValue(getDataAttrIndex(), (long) val);
                        } else {
                            BigDecimal bigDecimal = new BigDecimal(val);
                            bigDecimal = bigDecimal.setScale(scala);
                            copyInstances.get(j).setValue(getDataAttrIndex(), bigDecimal.doubleValue());
                        }
                    }
                }
                startIndex = -1;
                isMiss = false;
            }
        }
    }


    /**
     * 一阶差分
     *
     * @param instances
     * @return
     */
    public Instances difference(Instances instances) {

        /**
         * 判断是否为递增波，如果不是，则不做差分，返回当前instances
         */
        Instances diffInstances = instances.stringFreeStructure();
        for (int i = 0; i < instances.numInstances() - 1; i++) {
            Instance currInstance = instances.get(i);
            Instance nextInstance = instances.get(i + 1);
            double[] values = new double[currInstance.numAttributes()];
            for (int j = 0; j < nextInstance.numAttributes(); j++) {
                if (j == getTimeAttrIndex()) {
                    values[j] = nextInstance.value(j);
                } else {
                    values[j] = nextInstance.value(getDataAttrIndex()) - currInstance.value(getDataAttrIndex());
                }
            }
            diffInstances.add(new DenseInstance(1.0, values));
        }
        diffInstances.forEach(instance -> {
            if (instance.value(getDataAttrIndex()) < 0) {
                instance.setValue(getDataAttrIndex(), 0);
            }
        });

        return diffInstances;
    }


    /**
     * 异常检查
     *
     * @param instances
     * @throws Exception
     */
    public Map<Integer, double[]> anomalyDetection(Instances instances, Instances normalChannelInstances) {

        Map<Integer, double[]> outlierMap = new LinkedHashMap<>();

        int lastIndex = instances.numInstances() - 1;
        double lastValue = instances.get(lastIndex).value(getDataAttrIndex());
        double sumExcludeLast = 0;
        for (int i = 0; i < instances.size() - 1; i++) {
            sumExcludeLast += instances.get(i).value(getDataAttrIndex());
        }
        if (sumExcludeLast == 0 && lastValue > 0) {
            outlierMap = new LinkedHashMap<>();
            double threshold = normalChannelInstances.get(instances.size() - 1).value(getDataAttrIndex());
            outlierMap.put(lastIndex, new double[]{threshold, (lastValue - threshold) / threshold});
        } else {
            for (int i = 0; i < instances.numInstances(); i++) {
                double value = instances.get(i).value(getDataAttrIndex());
                double threshold = normalChannelInstances.get(i).value(getDataAttrIndex());
                if (value > threshold) {
                    outlierMap.put(i, new double[]{threshold, (value - threshold) / threshold});
                }
            }
        }

        return outlierMap;
    }


    /**
     * 季节性平移
     *
     * @param instances
     * @return
     */
    public Instances verticalTranslationSeasonal(Instances instances) {
        Instances vtInstances = instances.stringFreeStructure();
        double[] values = instances.attributeToDoubleArray(getDataAttrIndex());
        Decomposition decomposition = new Decomposition(values, period);
        decomposition.decompose();
        double[] seasonal = decomposition.getSeasonal();
        Percentile percentile = new Percentile();
        double pValue = percentile.evaluate(seasonal, ThresholdConfig.getPercentage());
        List<Double> normalList = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= pValue)
                normalList.add(seasonal[i]);
        }
        double[] normals = normalList.stream().mapToDouble(d -> d).toArray();
        double lambda = NumericTools.getMean(values) + 2.5 * NumericTools.getStdDev(normals);
        for (int i = 0; i < seasonal.length; i++) {
            double[] vals = new double[instances.numAttributes()];
            vals[getTimeAttrIndex()] = instances.get(i).value(getTimeAttrIndex());
            vals[getDataAttrIndex()] = seasonal[i] + ((Double.isNaN(lambda) || Double.isInfinite(lambda)) ? 0.0 : lambda);
            vtInstances.add(new DenseInstance(1.0, vals));
        }

        return vtInstances;
    }

    /**
     * data
     * 无效数据检查
     *
     * @param dataMap
     * @return
     */
    protected boolean isInvalidData(Map<String, List<Object>> dataMap) {
        if (dataMap == null)
            return true;
        Iterator<String> iter = dataMap.keySet().iterator();
        List<Object> list = null;

        long endTime = 0;
        while (iter.hasNext()) {
            list = dataMap.get(iter.next());
//            Object object = list.get(0);
//            if (object instanceof String && !NumericTools.isNumeric(String.valueOf(object))) {
//                String format = DateUtils.getDateFormat(String.valueOf(object));
//                if (format == null || "".equals(format))
//                    continue;
//                list.sort((o1, o2) -> {
//                    long t1 = DateUtils.timeToLong(String.valueOf(o1), format);
//                    long t2 = DateUtils.timeToLong(String.valueOf(o2), format);
//                    if (t1 < t2)
//                        return -1;
//                    return 1;
//                });
//                endTime = DateUtils.timeToLong(String.valueOf(list.get(list.size() - 1)), format);
//                break;
//            }
            break;
        }

//        String lastDate = DateUtils.getLastDate(DateUtils.PATTERN_DAY_2);
//        long lastTimestamp = DateUtils.timeToLong(lastDate, DateUtils.PATTERN_DAY_2);
//
//        if (endTime < lastTimestamp)
//            return true;

        if (list.size() < ThresholdConfig.getMinAmount())
            return true;

        return false;
    }

    /**
     * 判断数据是否是递增的波
     *
     * @return
     */
    protected boolean isIncrementalWave(double[] values) {
        double up = 0, down = 0;
        for (int i = 0; i < values.length - 1; i++) {
            if (values[i + 1] - values[i] <= 0) {
                down += 1;
            } else {
                up += 1;
            }
        }

//        if (down / up < up / values.length)
        if (up / values.length >= 0.7)
            return true;
        return false;
    }


    public int getTimeAttrIndex() {
        return timeAttrIndex;
    }

    public void setTimeAttrIndex(int timeAttrIndex) {
        this.timeAttrIndex = timeAttrIndex;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTimeStampField() {
        return timeStampField;
    }

    public void setTimeStampField(String timeStampField) {
        this.timeStampField = timeStampField;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public String getDataField() {
        return dataField;
    }

    public void setDataField(String dataField) {
        this.dataField = dataField;
    }

    public int getDataAttrIndex() {
        return dataAttrIndex;
    }

    public void setDataAttrIndex(int dataAttrIndex) {
        this.dataAttrIndex = dataAttrIndex;
    }


    public Units getUnit() {
        return unit;
    }

    public void setUnit(Units unit) {
        this.unit = unit;
    }

    public String getPeriodicity() {
        return getUnit().name();
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public long getTolerance() {
        return tolerance;
    }

    public void setTolerance(long tolerance) {
        this.tolerance = tolerance;
    }
}
