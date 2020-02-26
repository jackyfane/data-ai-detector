package com.yaomalang.aidetector.tools;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Create Date : 27/12/2017 10:21
 * @ModificalHistory :
 */

@Slf4j
@Data
public class ThresholdConfig {

    static {
        InputStream is = ThresholdConfig.class.getResourceAsStream("/threshold.properties");
        Properties prop = new Properties();
        try {
            prop.load(is);
        } catch (IOException e) {
            log.error("please check threshold.properties is exists！");
        }
        ThresholdConfig.setMinAmount(Integer.valueOf(prop.getProperty("min_amount", "2")));
        ThresholdConfig.setMinModel(Integer.valueOf(prop.getProperty("min_model", "15")));
        ThresholdConfig.setIncrementalRate(Double.valueOf(prop.getProperty("incremental_rate", "0.3")));
        ThresholdConfig.setPercentage(Integer.valueOf(prop.getProperty("percentage", "95")));
    }


    @Setter
    @Getter
    private static int minAmount;
    @Setter
    @Getter
    private static int minModel;
    @Setter
    @Getter
    private static double incrementalRate;
    @Setter
    @Getter
    private static int percentage;
}
