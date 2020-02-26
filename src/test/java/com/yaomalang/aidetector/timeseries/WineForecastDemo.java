package com.yaomalang.aidetector.timeseries;

import weka.classifiers.evaluation.NumericPrediction;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.timeseries.WekaForecaster;
import weka.classifiers.timeseries.eval.TSEvaluation;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

import java.io.FileReader;
import java.util.List;

public class WineForecastDemo {

    public static void main(String[] args) throws Exception {

//        String filePath = "data/PD_DB_AWR_SYSSTAT.csv";
//        CSVLoader csvLoader = new CSVLoader();
//        csvLoader.setSource(new File(filePath));
//        Instances data = csvLoader.getDataSet();


        String filePath = "data/airline.arff";
//        HoltWinters holtWinters = new HoltWinters();
//        holtWinters.setTrendSmoothingFactor();


        Instances data = new Instances(new FileReader(filePath));
        String[] options = new String[2];
        options[0] = "-R";
        options[1] = "2";

        StringToNominal convert = new StringToNominal();
        convert.setOptions(options);
        convert.setInputFormat(data);
        Instances newInstances = Filter.useFilter(data, convert);


        WekaForecaster forecaster = new WekaForecaster();

        try {

            forecaster.setFieldsToForecast("passenger_numbers");
//            forecaster.setFieldsToForecast("DB_BLOCK_CHANGES");

            forecaster.setBaseForecaster(new GaussianProcesses());

            forecaster.getTSLagMaker().setTimeStampField("Date");
//            forecaster.getTSLagMaker().setTimeStampField("SNAP_TIME");
            forecaster.getTSLagMaker().setMinLag(1);
            forecaster.getTSLagMaker().setMaxLag(12);
            forecaster.getTSLagMaker().setAddMonthOfYear(true);
            forecaster.getTSLagMaker().setAddQuarterOfYear(true);

            forecaster.buildForecaster(data, System.out);
            forecaster.primeForecaster(data);

            List<List<NumericPrediction>> forecast = forecaster.forecast(24, System.out);

            for (int i = 0; i < forecast.size(); i++) {
                List<NumericPrediction> predsAtStep = forecast.get(i);
                for (int j = 0; j < predsAtStep.size(); j++) {
                    NumericPrediction predForTarget = predsAtStep.get(j);
                    System.out.println("" + predForTarget.predicted() + "");
                }
//                System.out.println();
            }

            // a new evaluation object (evaluation on the training data)
            TSEvaluation eval = new TSEvaluation(data, 0);

            // generate and evaluate predictions for up to 24 steps ahead
            eval.setHorizon(24);

            // prime with enough data to cover our maximum lag
//            eval.setPrimeWindowSize(12);
            eval.evaluateForecaster(forecaster, System.out);
            System.out.println(eval.toSummaryString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
