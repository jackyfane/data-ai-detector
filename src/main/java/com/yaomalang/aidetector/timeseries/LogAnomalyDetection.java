package com.yaomalang.aidetector.timeseries;

import com.yaomalang.aidetector.analysis.IntelligentBethune;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Create Date : 2017/12/20 15:33
 * @ModificalHistory :
 */
public class LogAnomalyDetection extends TimeSeries implements IntelligentBethune<Map<String, List<Object>>, List<Long>> {

    private Map<String, List<Object>> dataMap;
    private Instances instances;
    private Map<Integer, double[]> abnormalMap;

    public LogAnomalyDetection(Map<String, List<Object>> dataMap) {
        this.dataMap = dataMap;
    }


    @Override
    public void predict() throws Exception {
        this.instances = createInstances(dataMap);
        this.instances = this.replaceMissingValues(this.instances);
        confirmPeriodicity(instances);
        Instances normalChannels = verticalTranslationSeasonal(this.instances);
        abnormalMap = anomalyDetection(this.instances, normalChannels);
    }

    @Override
    public List<Long> getResults() throws Exception {

        if (abnormalMap == null && abnormalMap.isEmpty())
            return null;
        List<Long> abnormalTimePointList = new ArrayList<>();
        for (Map.Entry<Integer, double[]> abnormal : abnormalMap.entrySet()) {
            abnormalTimePointList.add((long) this.instances.get(abnormal.getKey()).value(getTimeAttrIndex()));
        }
        abnormalTimePointList.sort((o1, o2) -> {
            if (o1 == o2)
                return 0;
            if (o1 > o2)
                return 1;
            return -1;

        });

        return abnormalTimePointList;
    }

    @Override
    public boolean isLessData() {
        return isInvalidData(this.dataMap);
    }
}
