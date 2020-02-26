package com.yaomalang.aidetector.timeseries;

import com.yaomalang.aidetector.analysis.IntelligentBethune;
import com.yaomalang.aidetector.tools.DateUtils;
import com.yaomalang.aidetector.tools.ThresholdConfig;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import weka.classifiers.evaluation.NumericPrediction;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.timeseries.WekaForecaster;
import weka.core.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.List;

public class TimeSeriesForecast extends TimeSeries implements IntelligentBethune<Map<String, List<Object>>, Map<String, List<Object>>> {

    private Map<String, List<Object>> dataMap;
    private double threshold = 0.0d;

    private Instances instances;
    private Instances resultInstances;
    private Instances smoothedInstances;
    private Instances seasonalInstances;
    private Instances trendInstances;

    private int maxPredicts;
    private long maxTime;

    public TimeSeriesForecast(int maxPredicts) {
        this.maxPredicts = maxPredicts;
    }

    /**
     * @param dataMap
     * @param threshold
     * @param maxPredicts
     */
    public TimeSeriesForecast(Map<String, List<Object>> dataMap, double threshold, int maxPredicts) {
        this(dataMap, threshold, 0, maxPredicts);
    }

    /**
     * @param dataMap   时间序列，格式为{"timeFieldName":["2017", "2018", "2019"], "valueAttrName":[1232, 3240, 2343]}
     *                  Map的key可以任意指定，并且key的值可以是乱序的，但是必须确保timeFieldName和valueFieldName的List中的值
     *                  一一对应，并且两个集合的长度必须相等
     * @param threshold 阀值
     * @param steps     期望预测的数据量
     */
    public TimeSeriesForecast(Map<String, List<Object>> dataMap, double threshold, int steps, int maxPredicts) {
        this.dataMap = dataMap;
        this.threshold = threshold;
        this.maxPredicts = maxPredicts;
        setSteps(steps);
    }

    /**
     * @param instances
     * @param threshold
     * @param steps
     */
    public TimeSeriesForecast(Instances instances, double threshold, int steps) {
        this.instances = instances;
        this.threshold = threshold;
        setSteps(steps);
    }


    /**
     * 初始化函数
     */
    public void init() throws Exception {
        if (this.dataMap != null) {
            this.instances = createInstances(this.dataMap);
        } else {
            confirmAttributeInfo(this.instances.get(0));
            this.instances.sort(getTimeAttrIndex());
        }
        this.seasonalInstances = this.instances.stringFreeStructure();
        this.trendInstances = this.instances.stringFreeStructure();
        this.resultInstances = this.instances.stringFreeStructure();

//        this.save(this.instances, getDataField());
        this.smoothedInstances = this.replaceMissingValues(instances);
//        this.save(this.smoothedInstances, getDataField() + "_ADDED");
        this.confirmPeriodicity(this.smoothedInstances);
        this.addMissedTimeToYesterday(this.smoothedInstances);

        //根据公差和给定的最大预测量，设置最大预测时间
        this.maxTime = (long) this.instances.lastInstance().value(getTimeAttrIndex());
        this.maxTime += this.getTolerance() * (this.maxPredicts + 1);
    }

    @Override
    public void predict() throws Exception {
        this.init();
        if (this.smoothedInstances.numInstances() >= ThresholdConfig.getMinModel()) {
            this.smoothedInstances = smoothingInstances(this.smoothedInstances);
//            this.save(this.smoothedInstances, getDataField() + "_SMOOTHED");
            predictByWeka();
//            predictBySeasonal();
        }
    }

    /**
     * 根据具有周期的季节性数据进行预测
     */
    private void predictBySeasonal() {
        this.trendInstances.setClassIndex(getDataAttrIndex());
        LinearRegression regression = new LinearRegression();
        double[] trends = new double[2];
        try {
            regression.buildClassifier(this.trendInstances);
            for (int i = 0; i < trends.length; i++) {
                trends[i] = regression.classifyInstance(this.trendInstances.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        double trend = trends[1] - trends[0];
        double sum = 0;

        int period = 0;
        if (this.seasonalInstances.numInstances() < getPeriod()) {
            period = this.seasonalInstances.numInstances();
        } else {
            period = getPeriod();
        }

        if (this.threshold > 0) {
            while (this.threshold > sum) {
                if (this.seasonalInstances.numInstances() >= getPeriod()) {
                    period = getPeriod();
                }
                Instances insts = new Instances(this.seasonalInstances, this.seasonalInstances.numInstances() - period, period);
                for (int i = 0; i < period; i++) {
                    Instance instance = insts.get(i);
                    double[] values = new double[instance.numAttributes()];
                    for (int j = 0; j < values.length; j++) {
                        if (j == getTimeAttrIndex()) {
                            values[j] = instance.value(getTimeAttrIndex()) + period * getTolerance();
                        } else {
                            double value = instance.value(getDataAttrIndex()) + trend;
                            values[j] = value;
                            sum += value;
                        }
                    }
                    if (this.threshold < sum || this.resultInstances.numInstances() >= this.maxPredicts) {
                        sum = this.threshold;
                        break;
                    }
                    instance = new DenseInstance(1.0, values);
                    this.resultInstances.add(instance);
                    this.seasonalInstances.add(instance);
                }
            }
        } else {
            if (this.seasonalInstances.numInstances() < getSteps()) {
                period = this.seasonalInstances.numInstances();
            } else {
                period = getPeriod();
            }
            Instances insts = new Instances(this.seasonalInstances, this.seasonalInstances.numInstances() - period, period);
            for (int i = 0; i < getSteps(); i++) {
                Instance instance = insts.get(i);
                double[] values = new double[instance.numAttributes()];
                for (int j = 0; j < instance.numAttributes(); j++) {
                    if (j == getTimeAttrIndex()) {
                        values[j] = instance.value(j) + getTolerance() * period;
                    } else {
                        values[j] = instance.value(j) + trend;
                    }
                }
                this.resultInstances.add(new DenseInstance(1.0, values));
            }
        }
    }

    /**
     * @throws Exception
     */
    private void predictByWeka() throws Exception {

        if (this.threshold > 0) {
            double sum = 0.0d;
            boolean isOver = false;
            while (!isOver) {
                Instances forecastInstances = this.forecast();
                Instances differences = difference(forecastInstances);
                for (int i = 0; i < differences.numInstances(); i++) {
                    sum += differences.get(i).value(getDataAttrIndex());
                    if (this.threshold <= sum || resultInstances.numInstances() > maxPredicts) {
                        isOver = true;
                        break;
                    }
                    this.resultInstances.add(forecastInstances.get(i));
                }
                if (!isOver) {
                    this.resultInstances.add(forecastInstances.lastInstance());
                }
            }
        } else {
            this.resultInstances = forecast();
        }
    }

    @Override
    public Map<String, List<Object>> getResults() throws NullPointerException {
        if (this.resultInstances == null)
            throw new NullPointerException("Forecast result instances is null or empty. The method must be called after predict() and forecast().");
        Map<String, List<Object>> predictMap = new HashMap<>();
        predictMap.put(getTimeStampField(), new ArrayList());
        predictMap.put(getDataField(), new ArrayList());
        resultInstances.forEach((Instance instance) -> {
            for (int i = 0; i < instance.numAttributes(); i++) {
                String attrName = instance.attribute(i).name();
                if (instance.attribute(i).isDate()) {
                    predictMap.get(attrName).add(DateUtils.parserToString((long) instance.value(getTimeAttrIndex()), DateUtils.PATTERN_SECOND_1));
                } else {
                    predictMap.get(attrName).add(instance.value(getDataAttrIndex()));
                }
            }
        });

        return predictMap;
    }

    @Override
    public boolean isLessData() {
        return isInvalidData(this.dataMap);
    }


    /**
     * 如果最大时间不为当前前一天的时间，则按照平均值进行累加补全数据
     *
     * @param instances
     */
    private void addMissedTimeToYesterday(Instances instances) {
        long maxTime = (long) instances.lastInstance().value(getTimeAttrIndex());
        String lastDate = DateUtils.getLastDate(DateUtils.PATTERN_DAY_2);
        long lastTime = DateUtils.timeToLong(lastDate, DateUtils.PATTERN_DAY_2);
        if (maxTime < lastTime) {
//            double mean = difference(instances).meanOrMode(getDataAttrIndex());
            int missedCounts;
            switch (getUnit().name()) {
                case "SECONDLY":
                    missedCounts = (int) ((lastTime - maxTime) / 1000) + 59;
                    break;
                case "MINUTELY":
                    missedCounts = (int) ((lastTime - maxTime) / (60 * 1000)) + 59;
                    break;
                case "HOURLY":
                    missedCounts = (int) ((lastTime - maxTime) / (60 * 60 * 1000)) + 23;
                    break;
                default:
                    missedCounts = (int) ((lastTime - maxTime) / (24 * 60 * 60 * 1000));
                    break;
            }

            for (int i = 0; i < missedCounts; i++) {
                double[] values = new double[2];
                values[getTimeAttrIndex()] = maxTime + (i + 1) * getTolerance();
                values[getDataAttrIndex()] = instances.lastInstance().value(getDataAttrIndex());
                instances.add(new DenseInstance(1.0, values));
            }
        }
//        System.out.println(instances.toSummaryString());
//        System.out.println(instances);
    }


    /**
     * 平滑数据
     *
     * @param instances
     * @return
     */
    protected Instances smoothingInstances(Instances instances) {

        Instances normalChannelInstances = verticalTranslationSeasonal(instances);
        Map<Integer, double[]> outliersMap = anomalyDetection(instances, normalChannelInstances);

        // 构建新的平滑数据集
        Instances smoothInstances = instances.stringFreeStructure();
        for (int i = 0; i < instances.numInstances(); i++) {
            Instance instance = instances.get(i);
            double[] smoothed = outliersMap.get(i);
            if (smoothed != null) {
                double[] values = new double[instance.numAttributes()];
                values[getTimeAttrIndex()] = instances.get(i).value(getTimeAttrIndex());
                values[getDataAttrIndex()] = smoothed[0];
                instance = new DenseInstance(instance.weight(), values);
            }
            smoothInstances.add(instance);
        }

        return smoothInstances;

    }

    /**
     * 预测并返回预测结果
     *
     * @return
     */
    protected Instances forecast() throws Exception {

        if (this.resultInstances != null && this.resultInstances.numInstances() > 0) {
            int nums = this.resultInstances.numInstances();
            for (int i = nums - getSteps(); i < this.resultInstances.numInstances(); i++) {
                this.smoothedInstances.add(this.resultInstances.get(i));
            }
        }

        long lastTime = (long) this.smoothedInstances.lastInstance().value(getTimeAttrIndex());

        WekaForecaster forecaster = getWekaForecaster(this.smoothedInstances);

        if (this.smoothedInstances.numInstances() < 30 && getUnit() == Units.DAILY) {
            setSteps(7);
        } else {
            confirmPeriodicity(this.smoothedInstances);
        }
        List<List<NumericPrediction>> forecast = forecaster.forecast(getSteps());
        Instances forecastInstances = instances.stringFreeStructure();
        for (int i = 0; i < getSteps(); i++) {
            List<NumericPrediction> predsAtStep = forecast.get(i);
            double[] values = new double[forecastInstances.numAttributes()];
            values[getTimeAttrIndex()] = lastTime + getTolerance() * (i + 1);
            double predicted = predsAtStep.get(0).predicted();
            if (Double.isNaN(predicted) || Double.isInfinite(predicted)) {
                predicted = this.smoothedInstances.lastInstance().value(getDataAttrIndex());
            }
            values[getDataAttrIndex()] = predicted;
            forecastInstances.add(new DenseInstance(1.0, values));
        }

        return forecastInstances;
    }

    /**
     * 分解数据集为季节性数据和周期性数据
     *
     * @param instances
     * @return
     */
    protected void decomposeInstances(Instances instances) {
        double[] values = instances.attributeToDoubleArray(getDataAttrIndex());
        Decomposition decomposition = new Decomposition(values, getPeriod());
        decomposition.decompose();
        double[] seasonal = decomposition.getSeasonal();
        double[] trends = decomposition.getTrend();

        Percentile percentile = new Percentile();
        double quantile = percentile.evaluate(seasonal, ThresholdConfig.getPercentage());
        List<Double> doubleList = new ArrayList<>();
        for (int i = 0; i < seasonal.length; i++) {
            if (seasonal[i] < quantile)
                doubleList.add(seasonal[i]);
        }
        StandardDeviation deviation = new StandardDeviation();
        double std = deviation.evaluate(doubleList.stream().mapToDouble(d -> d).toArray());

        for (int i = 0; i < instances.numInstances(); i++) {
            Instance instance = instances.get(i);
            double[] sValues = new double[instance.numAttributes()];
            double[] tValues = new double[instance.numAttributes()];

            sValues[getTimeAttrIndex()] = instance.value(getTimeAttrIndex());
            sValues[getDataAttrIndex()] = seasonal[i] + 1.3 * std;

            tValues[getTimeAttrIndex()] = instance.value(getTimeAttrIndex());
            tValues[getDataAttrIndex()] = trends[i];

            this.seasonalInstances.add(new DenseInstance(1.0, sValues));
            this.trendInstances.add(new DenseInstance(1.0, tValues));
        }
    }


    /**
     * 经过平滑的数据
     *
     * @return
     */
    public Map<String, List<Object>> getSmoothedData() {

        Map<String, List<Object>> smoothedMap = new HashMap<>();
        smoothedMap.put(getTimeStampField(), new ArrayList());
        smoothedMap.put(getDataField(), new ArrayList());
        for (int i = 0; i < this.instances.numInstances(); i++) {
            Instance instance = this.smoothedInstances.get(i);
            for (int j = 0; j < instance.numAttributes(); j++) {
                Attribute attribute = instance.attribute(j);
                if (attribute.isDate()) {
                    smoothedMap.get(attribute.name()).add(instance.stringValue(attribute));
                } else {
                    smoothedMap.get(attribute.name()).add(instance.value(attribute));
                }
            }
        }

        return smoothedMap;
    }

    /**
     * 告警的时间
     *
     * @return
     */
    public Date getAlarmDate() {
        long dateTime = 0l;
        int days = 0;
        if (this.smoothedInstances.size() < ThresholdConfig.getMinModel()) {
            double mean = this.difference(this.smoothedInstances).meanOrMode(getDataAttrIndex());
            if (mean <= 0 || Double.isInfinite(mean) || Double.isNaN(mean)) {
                days = this.getMaxPredicts();
            } else {
                days = (int) (this.threshold / mean);
            }
        } else {
            if (this.resultInstances == null || this.resultInstances.numInstances() < 1)
                return null;
            dateTime = (long) this.resultInstances.get(resultInstances.numInstances() - 1).value(getTimeAttrIndex());
        }
        if (days > 0) {
            long lastTime = (long) this.instances.lastInstance().value(getTimeAttrIndex());
            dateTime = DateUtils.getDateByAssignStartDateAndDays(new Date(lastTime), days).getTime();
        }
        if (dateTime > this.maxTime) {
            System.out.println("Predict Date : " + DateUtils.parserToString(dateTime, DateUtils.PATTERN_DAY_1));
            dateTime = this.maxTime;
            System.out.println("Result Date : " + DateUtils.parserToString(dateTime, DateUtils.PATTERN_DAY_1));
        }

        return new Date(dateTime);
    }

    /**
     * 获得告警天数
     *
     * @return
     */
    public double getAlarmDays() {
        double days = 0;
        if (this.smoothedInstances.size() < ThresholdConfig.getMinModel()) {
            double mean = this.difference(this.smoothedInstances).meanOrMode(getDataAttrIndex());
            if (mean <= 0 || Double.isInfinite(mean) || Double.isNaN(mean)) {
                days = this.getMaxPredicts();
            } else {
                days = (int) (this.threshold / mean);
            }
        } else {
            int nums = this.resultInstances.numInstances();
            double first = this.resultInstances.get(0).value(getTimeAttrIndex());
            double last = this.resultInstances.get(nums - 1).value(getTimeAttrIndex());
            days = (last - first) / this.getTolerance();
        }

        BigDecimal bigDecimal = new BigDecimal(days > this.maxPredicts ? this.maxPredicts : days);
        BigDecimal result = bigDecimal.setScale(1, BigDecimal.ROUND_DOWN);
        return result.doubleValue();
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }


    public void setInstances(Instances instances) {
        this.instances = instances;
    }

    public Instances getInstances() {
        return instances;
    }

    public Map<String, List<Object>> getDataMap() {
        return dataMap;
    }

    public void setDataMap(Map<String, List<Object>> dataMap) {
        this.dataMap = dataMap;
    }

    public int getMaxPredicts() {
        return maxPredicts;
    }

    public void setMaxPredicts(int maxPredicts) {
        this.maxPredicts = maxPredicts;
    }
}
