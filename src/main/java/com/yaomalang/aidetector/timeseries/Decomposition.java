package com.yaomalang.aidetector.timeseries;

import com.github.servicenow.ds.stats.stl.SeasonalTrendLoess;

/**
 * 使用STL算法，讲时间序列拆解为seasonal，trend，residual
 *
 * @author fanwenyong
 * @createdTime
 */
public class Decomposition {

    private int period;
    private double[] values;

    private double[] seasonal;
    private double[] trend;
    private double[] residual;


    public Decomposition(double[] values, int period) {
        this.values = values;
        this.period = period;
    }

    /**
     * 将时间序列数据解析为季节、趋势
     *
     * @throws Exception
     */
    public void decompose() {
        SeasonalTrendLoess.Builder builder = new SeasonalTrendLoess.Builder();

        SeasonalTrendLoess smoother = builder.setPeriodic()
                .setPeriodLength(this.values.length / this.period < 2 ? this.values.length / 2 : this.period)
                .setNonRobust()
                .buildSmoother(this.values);
        SeasonalTrendLoess.Decomposition stl = smoother.decompose();
        this.seasonal = stl.getSeasonal();
        this.trend = stl.getTrend();
        this.residual = stl.getResidual();
    }

    public double[] getSeasonal() {
        return this.seasonal;
    }

    public double[] getTrend() {
        return this.trend;
    }

    public double[] getResidual() {
        return this.residual;
    }
}
