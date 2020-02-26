package com.yaomalang.aidetector.timeseries;

import com.yaomalang.aidetector.analysis.IntelligentBethune;
import com.yaomalang.aidetector.tools.DateUtils;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Create Date : 2017/12/21 09:38
 * @ModificalHistory :
 */
public class LogAnomalyDetectionTest extends TestCase {

    @Test
    public void testLogAnomaly() {
        Map<String, List<Object>> dataMap = Reader.loadProdData("developer_log.csv");
        IntelligentBethune<Map<String, List<Object>>, List<Long>> bethune = new LogAnomalyDetection(dataMap);
        try {
            if (!bethune.isLessData()) {
                bethune.predict();
                List<Long> anomalyPoints = bethune.getResults();
                for (Long point : anomalyPoints) {
                    System.out.println(DateUtils.parserToString(point, DateUtils.PATTERN_SECOND_1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
