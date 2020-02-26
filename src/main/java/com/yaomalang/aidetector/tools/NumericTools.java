package com.yaomalang.aidetector.tools;

import java.util.regex.Pattern;

public final class NumericTools {

    /**
     * 双精度和浮点型
     *
     * @param str
     * @return
     */
    public static boolean isDouble(String str) {
        Pattern pattern = Pattern.compile("^[-\\+]?[.\\d]*$");
        return pattern.matcher(str).matches();
    }

    /**
     * 整型
     *
     * @param str
     * @return
     */
    public static boolean isInteger(String str) {
        Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");
        return pattern.matcher(str).matches();
    }

    /**
     * 数字类型数据
     *
     * @param str
     * @return
     */
    public static boolean isNumeric(String str) {
        if (isDouble(str) || isInteger(str))
            return true;
        return false;
    }


    /**
     * 平均值
     */
    public static double getMean(double[] values) {
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
        }
        double mean = sum / values.length;

        return mean;
    }

    /**
     * 方差
     *
     * @return
     */
    public static double getVariance(double[] values) {
        double mu = getMean(values);
        double var = 0;
        for (int i = 0; i < values.length; i++) {
            var += Math.pow(values[i] - mu, 2);
        }
        return var / values.length-1;
    }

    /**
     * 标准差
     *
     * @return
     */
    public static double getStdDev(double[] values) {
        double var = getVariance(values);
        return Math.sqrt(var);
    }

    /**
     * 和
     *
     * @param values
     * @return
     */
    public static double getSum(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    /**
     * 比率
     *
     * @param values
     * @return
     */
    public static double[] getRate(double[] values) {
        if (values.length == 1)
            return new double[]{1.0};

        double[] rate = new double[values.length];
        double sum = getSum(values);
        for (int i = 0; i < values.length; i++) {
            rate[i] = values[i] / sum;
        }
        return rate;

    }
}
