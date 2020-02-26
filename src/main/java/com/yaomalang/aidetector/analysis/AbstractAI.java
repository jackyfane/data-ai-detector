package com.yaomalang.aidetector.analysis;

import com.yaomalang.aidetector.tools.DateUtils;
import com.yaomalang.aidetector.tools.NumericTools;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Add;
import weka.filters.unsupervised.instance.RemoveDuplicates;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author fanwenyong
 * @time 2017-10-23 PM 2:30
 */
public abstract class AbstractAI {

    /**
     * 构建WEKA实例集
     *
     * @param relation
     * @param attributes
     * @param dataSets
     * @return
     */
    public Instances buildInstances(String relation, String[] attributes, Object[][] dataSets) throws Exception {

        ArrayList<String> attrList = new ArrayList<>();
        for (String attribute : attributes) {
            attrList.add(attribute);
        }
        return buildInstances(relation, attrList, dataSets);
    }

    /**
     * 构建WEKA实例集
     *
     * @param relation
     * @param attributes
     * @param dataSets
     * @return
     */
    public Instances buildInstances(String relation, List<String> attributes, Object[][] dataSets) throws Exception {

        ArrayList<Attribute> attrList = new ArrayList<>();
        for (String attribute : attributes) {
            attrList.add(new Attribute(attribute));
        }
        return buildInstances(relation, attrList, dataSets);
    }

    /**
     * 构建WEKA实例集
     *
     * @param relation
     * @param attributes
     * @param dataSets
     * @return
     */
    public Instances buildInstances(String relation, ArrayList<Attribute> attributes, Object[][] dataSets) throws Exception {

        if (attributes.size() != dataSets.length)
            throw new IllegalArgumentException("属性个数与数据列数不一致, 请检查您的数据是否正确!");

        String format = null;

        Instances instances = getInstances(relation, dataSets[0].length);
        for (int i = 0; i < attributes.size(); i++) {
            Object object = dataSets[i][0];

            String value = String.valueOf(object);
            String[] options = new String[6];
            options[0] = "-N";
            options[1] = attributes.get(i).name();
            options[2] = "-C";
            options[3] = "last";
            options[4] = "-T";
            if (object instanceof String && !NumericTools.isNumeric(value)) {
                format = DateUtils.getDateFormat(value);
                if (format != null && !"".equals(format)) {
                    options[5] = "DAT";
                } else {
                    options[5] = "STR";
                }
            } else {
                options[5] = "NUM";
            }
            Add filter = new Add();
            filter.setOptions(options);
            filter.setInputFormat(instances);
            instances = Filter.useFilter(instances, filter);
        }
        instances.deleteAttributeAt(0);

        for (int i = 0; i < instances.numInstances(); i++) {
            for (int j = 0; j < instances.numAttributes(); j++) {
                Object object = dataSets[j][i];
                String value = String.valueOf(object);
                Attribute attribute = instances.attribute(j);
                if (attribute.isDate()) {
                    instances.get(i).setValue(j, DateUtils.timeToLong(value, format));
                } else if (attribute.isNumeric()) {
                    instances.get(i).setValue(j, Double.valueOf(value));
                } else {
                    instances.get(i).setValue(j, value);
                }
            }
        }

        return instances;
    }

    /**
     * 创建一个临时Instances
     *
     * @param relation
     * @param length
     * @return
     */
    protected Instances getInstances(String relation, int length) {
        ArrayList<Attribute> tempAttribute = new ArrayList<>();
        tempAttribute.add(new Attribute("temp"));
        Instances instances = new Instances(relation, tempAttribute, 0);
        for (int i = 0; i < length; i++) {
            Instance instance = new DenseInstance(1.0, new double[]{i + 1});
            instances.add(instance);
        }
        return instances;
    }

    /**
     * 拷贝完整的一份Instances
     * @param instances
     * @return
     */
    public Instances copy(Instances instances) {
        Instances copyInstances = instances.stringFreeStructure();
        for (Instance instance : instances) {
            copyInstances.add(instance);
        }
        return copyInstances;
    }


    /**
     * 修复缺失值
     *
     * @param instances
     */
    public Instances replaceMissingValues(Instances instances) {

        return null;
    }


    /**
     * 去重
     *
     * @param copyInstances
     * @return
     */
    public Instances removeDuplicates(Instances copyInstances) {
        /**=================去重并排序======================*/
        try {
            RemoveDuplicates filter = new RemoveDuplicates();
            filter.setInputFormat(copyInstances);
            copyInstances = Filter.useFilter(copyInstances, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return copyInstances;
    }



    /**
     * 将创建的Instances保存到ARFF文件
     *
     * @param instances
     * @param filename
     * @throws Exception
     */
    public void save(Instances instances, String filename) throws Exception {
        File file = new File("data");
        if (!file.exists() || !file.isDirectory()) {
            file.mkdir();
        }
        String filePath = String.format(file.getPath() + "/%s.arff", filename);
        ArffSaver arffSaver = new ArffSaver();
        arffSaver.setInstances(instances);
        arffSaver.setFile(new File(filePath));
        arffSaver.setDestination(new File(filePath));
        arffSaver.writeBatch();
    }
}
