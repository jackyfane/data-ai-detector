package com.yaomalang.aidetector.analysis;

import com.yaomalang.aidetector.timeseries.Reader;
import com.github.servicenow.ds.stats.stl.SeasonalTrendLoess;
import smile.math.Math;
import smile.plot.*;
import smile.stat.distribution.KernelDensity;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Normalize;

import javax.swing.*;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;

public class KernelDensityDemo extends JPanel {

    private double[] seasonal;
    private double[] trend;

    public KernelDensityDemo(Instances instances) {
        super(new BorderLayout());

        double[] times = instances.attributeToDoubleArray(0);
        double[] values = instances.attributeToDoubleArray(1);
        double[] stands = null;
        double[] kdVals = instances.attributeToDoubleArray(1);

        try {
            Normalize filter = new Normalize();
            filter.setInputFormat(instances);
            Instances sInstances = Filter.useFilter(instances, filter);
            stands = sInstances.attributeToDoubleArray(1);
//            kdVals = sInstances.attributeToDoubleArray(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        decompose(values);

        double[][] originalData = new double[values.length][2];
        double[][] smoothlyData = new double[values.length][2];
        double[][] standData = new double[values.length][2];
        double[][] seasonalData = new double[values.length][2];

        KernelDensity kde = new KernelDensity(kdVals);
        double mu = kde.mean();
        double sigma = kde.sd();

        for (int i = 0; i < values.length; i++) {
            originalData[i][0] = times[i];
            originalData[i][1] = values[i];

            seasonalData[i][0] = times[i];
            seasonalData[i][1] = seasonal[i];

            standData[i][0] = times[i];
            standData[i][1] = stands[i];

            smoothlyData[i][0] = times[i];
            smoothlyData[i][1] = seasonal[i] + mu + 2.5 * sigma;
        }

        JPanel canvas = createCanvas();
        PlotCanvas linePlot = LinePlot.plot(originalData, Line.Style.SOLID, Color.BLUE);
        linePlot.setTitle("Original Time Series");
        canvas.add(linePlot);
//
        linePlot = LinePlot.plot(seasonal, Line.Style.SOLID, Color.BLUE);
        linePlot.setTitle("Seasonal");
        canvas.add(linePlot);

//        linePlot = LinePlot.plot(standData, Line.Style.SOLID, Color.BLUE);
//        linePlot.setTitle("Time Series Standardize");
//        canvas.add(linePlot);
        linePlot = LinePlot.plot(smoothlyData, Line.Style.SOLID, Color.BLUE);
        linePlot.setTitle("Smoothly Time Series");
        canvas.add(linePlot);

        Instances trainInstances = instances.stringFreeStructure();
        for (int i = 0; i < instances.numInstances(); i++) {
            Instance instance = instances.get(i);
            Instance inst = new DenseInstance(1.0, new double[]{instance.value(0), trend[i]});
            trainInstances.add(inst);
        }

        LinearRegression classifier = new LinearRegression();
        try {
            trainInstances.setClassIndex(1);
            classifier.buildClassifier(trainInstances);
            double[][] test1 = new double[instances.numInstances()][2];
            double[][] test2 = new double[instances.numInstances()][2];

            for (int i = 0; i < instances.numInstances(); i++) {
                test1[i][0] = times[i];
                test1[i][1] = trend[i];

                test2[i][0] = times[i];
                test2[i][1] = classifier.classifyInstance(instances.get(i));
            }
            linePlot = LinePlot.plot(test1, Line.Style.SOLID, Color.BLUE);
            linePlot.line(test2, Line.Style.SOLID, Color.RED);
            linePlot.setTitle("Trend");
            canvas.add(linePlot);
        } catch (Exception e) {
            e.printStackTrace();
        }

        double[][] kernelProb = new double[values.length][2];
        for (int i = 0; i < values.length; i++) {
            kernelProb[i][0] = kdVals[i];
            kernelProb[i][1] = kde.p(kdVals[i]);
        }
        linePlot = LinePlot.plot(kernelProb, Line.Style.SOLID, Color.BLUE);
        linePlot.setTitle("核概率密度");
        canvas.add(linePlot);

//        double[] data = new double[values.length];
//        GaussianDistribution gaussian = new GaussianDistribution(mu, sigma);
//        for (int i = 0; i < values.length; i++) {
//            data[i] = gaussian.rand();
//        }
//        PlotCanvas histogram = Histogram.plot(data, 50);
//        histogram.setTitle("Histogram");
//        canvas.add(histogram);
//
        double value = findValueInMaxKernelDensity(instances);
        System.out.println(value);
    }

    public static void main(String[] args) {

        try {
            Instances instances = Reader.getInstances();
            JFrame frame = new JFrame("Gaussian Distribution");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().add(new KernelDensityDemo(instances));
            frame.setVisible(true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    private JPanel createCanvas() {
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i <= 100; i += 10) {
            labelTable.put(new Integer(i), new JLabel(String.valueOf(i / 10)));
        }

        JSlider sigmaSlider = new JSlider(0, 50);
        sigmaSlider.setLabelTable(labelTable);
        sigmaSlider.setMajorTickSpacing(10);
        sigmaSlider.setMinorTickSpacing(2);
        sigmaSlider.setPaintTicks(true);
        sigmaSlider.setPaintLabels(true);

        JPanel optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("\u03C3:"));
        optionPane.add(sigmaSlider);

        JPanel canvas = new JPanel(new GridLayout(5, 1));
        add(canvas, BorderLayout.CENTER);

        return canvas;
    }

    /**
     * @param values
     */
    private void decompose(double[] values) {

        SeasonalTrendLoess.Builder builder = new SeasonalTrendLoess.Builder();
        SeasonalTrendLoess smoother = builder.setPeriodic()
                .setPeriodLength(24)
                .setNonRobust()
                .buildSmoother(values);
        SeasonalTrendLoess.Decomposition stl = smoother.decompose();
        seasonal = stl.getSeasonal();
        trend = stl.getTrend();
    }

    /**
     * 寻找核密度分布最大点对应的值
     *
     * @param instances
     */
    public double findValueInMaxKernelDensity(Instances instances) {
        Instance instance = instances.get(0);
        int index = -1;
        for (int i = 0; i < instance.numAttributes(); i++) {
            Attribute attr = instance.attribute(i);
            if (attr.isDate()) continue;
            index = i;
        }

        double[] dataSeries = instances.attributeToDoubleArray(index);
        double sd = Math.sd(dataSeries);
        double h = 1.06 * sd * Math.pow(dataSeries.length, -1.0 / 5);

        KernelDensity kde = new KernelDensity(dataSeries, h);
        double mu = kde.mean();
        Double maxKDP = null;
        index = -1;
        for (int i = 0; i < dataSeries.length; i++) {
            double kdp = kde.p(dataSeries[i]);
            if (maxKDP == null) {
                maxKDP = kdp;
                index = i;
                continue;
            }
            if (maxKDP < kdp) {
                maxKDP = kdp;
                index = i;
            }
        }
        return dataSeries[index];
    }

    private static double applyAsDouble(Object b) {
        return Double.valueOf(b.toString());
    }
}
