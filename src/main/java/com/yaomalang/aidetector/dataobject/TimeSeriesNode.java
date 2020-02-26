package com.yaomalang.aidetector.dataobject;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间序列节点
 */
public class TimeSeriesNode {

    public String name;

    private List<Object> timeList;

    private List<Double> dataList;

    private List<TimeSeriesNode> children;

    public TimeSeriesNode(String name) {
        this(name, (Long) null, null);
    }

    /**
     * @param name
     * @param time
     * @param value
     */
    public TimeSeriesNode(String name, Long time, Double value) {
        this.name = name;
        this.timeList = new ArrayList<>();
        this.dataList = new ArrayList<>();
        if (time != null)
            this.addTime(time);
        if (value != null)
            this.addValue(value);
    }

    /**
     * @param name
     * @param time
     * @param value
     */
    public TimeSeriesNode(String name, String time, Double value) {
        this.name = name;
        this.timeList = new ArrayList<>();
        this.dataList = new ArrayList<>();
        if (time != null)
            this.addTime(time);
        if (value != null)
            this.addValue(value);
    }

    /**
     * @return
     */
    public List<TimeSeriesNode> getChildren() {
        return this.children;
    }

    /**
     * @param children
     */
    public void setChildren(List<TimeSeriesNode> children) {
        this.children = children;
    }

    public void addChild(TimeSeriesNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    /**
     * @param time
     */
    public void addTime(Object time) {
        if (timeList == null) {
            timeList = new ArrayList<>();
        }
    }

    /**
     * @param value
     */
    public void addValue(double value) {
        if (dataList == null) {
            dataList = new ArrayList<>();
        }
        dataList.add(value);
    }

    /**
     * @param time
     * @param value
     */
    public void addTimeValue(Object time, double value) {
        this.addTime(time);
        this.addValue(value);
    }

    /**
     * @return
     */
    public boolean isLeaf() {
        if (this.children == null || this.children.size() < 1)
            return true;
        return false;
    }

    public TimeSeriesNode get(String name){

        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Object> getTimeList() {
        return timeList;
    }

    public void setTimeList(List<Object> timeList) {
        this.timeList = timeList;
    }

    public List<Double> getDataList() {
        return dataList;
    }

    public void setDataList(List<Double> dataList) {
        this.dataList = dataList;
    }
}
