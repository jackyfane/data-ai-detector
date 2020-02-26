package com.yaomalang.aidetector.timeseries;

import com.alibaba.fastjson.JSONObject;
import com.yaomalang.aidetector.analysis.IntelligentBethune;
import com.enmotech.bethunepro.common.datastructure.TimingValue;
import com.enmotech.bethunepro.common.datastructure.TreeNode;
import com.enmotech.bethunepro.common.datastructure.TreeNodeVisitor;
import junit.framework.TestCase;
import org.junit.Test;

import java.io.IOException;

/**
 * @Description : TODO
 * @Author : fanwenyong
 * @Create Date : 2017/10/25 14:57
 * @ModificalHistory :
 */

public class AnomalyDetectionTest extends TestCase {

    @Test
    public void testAnomalyDetection() throws IOException {

        TreeNode<TimingValue> root = Reader.readFromCSV0();
        IntelligentBethune<TreeNode<TimingValue>, JSONObject> bethune = new AnomalyDetection(root);
        try {
            if (!bethune.isLessData()) {
                bethune.predict();
                JSONObject jsonObject = bethune.getResults();
                System.out.println(jsonObject.toString());
            } else{
                System.out.println("当前数据量太少......");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    TreeNodeVisitor<TimingValue, Long> v1 = new TreeNodeVisitor<TimingValue, Long>() {
        long sumOfValue;

        @Override
        public void visit(TreeNode<TimingValue> node) {
            System.out.println(node);
            TimingValue tv = node.getData();
            sumOfValue += tv.value;
        }

        @Override
        public Long getResult() {
            return sumOfValue;
        }
    };
}
