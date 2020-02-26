package com.yaomalang.aidetector.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import weka.core.Instances;

import java.util.List;
import java.util.Map;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Create Date : 2017/12/20 16:00
 * @ModificalHistory :
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnomalyData {

    private String[] names;
    private double[] preUsedBytesArray;
    private List<Map<Integer, double[]>> outlierList;
    private List<Instances> normalChannels;
}
