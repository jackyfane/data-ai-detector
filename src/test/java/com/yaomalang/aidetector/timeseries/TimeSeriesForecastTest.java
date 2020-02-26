package com.yaomalang.aidetector.timeseries;


import com.yaomalang.aidetector.tools.DateUtils;
import junit.framework.TestCase;
import org.junit.Test;
import weka.core.Instances;

import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Email : jackyfane@gmail.com
 * @Create Date : 2017/10/10 14:57
 * @ModificalHistory :
 */
public class TimeSeriesForecastTest extends TestCase {

    @Test
    public void testForMap() throws IOException {

        Map<String, List<Object>> dataMap = Reader.loadProdData("USERS.csv");
        TimeSeriesForecast forecast = new TimeSeriesForecast(dataMap, 22883713024d, 365);
        print(forecast);
    }

    @Test
    public void testForInstances() throws Exception {
        final TimeSeriesForecast forecast = new TimeSeriesForecast(365);
        List<Instances> instancesList = Reader.getInstancesList();
        List<Double> thresholds = Reader.thresholds;
        int i = 0;
        for (Instances instances : instancesList) {
            forecast.setInstances(instances);
            forecast.setThreshold(thresholds.get(i));
            print(forecast);
            i++;
        }
    }

    private void print(TimeSeriesForecast forecast) {
        try {
            if (forecast == null) return;
            if (forecast.isLessData()) return;

            forecast.predict();
            System.out.println("--------------------------alarm date--------------------------");
            System.out.println("alarm days is : " + DateUtils.parserToString(forecast.getAlarmDate().getTime(), DateUtils.PATTERN_DAY_1));
            System.out.println("--------------------------alarm days--------------------------");
            System.out.println("alarm days is : " + forecast.getAlarmDays());
            System.out.println("--------------------------Predict Results--------------------------");
            System.out.println(forecast.getResults());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param forecast
     */
    private void toArrfFile(TimeSeriesForecast forecast) {
        List<Instances> instancesList = forecast.splitToSingleAttributeSeries(forecast.getInstances());
        instancesList.forEach(instances -> {
            String path = "/Users/kingcheng/Repositories/BethuneProject/OpsAssistant/bethunepro-bia/src/main/resources/data/";
            String fileName = instances.get(0).attribute(1).name() + ".arrf";
            try {
                forecast.save(instances, path + fileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
