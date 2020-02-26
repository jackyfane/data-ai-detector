package com.yaomalang.aidetector.analysis;

/**
 * 白求恩智能分析接口
 */
public interface IntelligentBethune<T extends Object, R> {

    /**
     * 预测
     *
     * @throws Exception
     */
    public void predict() throws Exception;

    /**
     * 预测结果
     *
     * @return
     * @throws Exception
     */
    public R getResults() throws Exception;

    /**
     * 判断数据量是否太少
     *
     * @return
     */
    public boolean isLessData();

}
