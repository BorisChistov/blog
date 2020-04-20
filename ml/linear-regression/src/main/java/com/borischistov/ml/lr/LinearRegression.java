package com.borischistov.ml.lr;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

public class LinearRegression {

    private static final Logger logger = LoggerFactory.getLogger(LinearRegression.class);
    private final Path exportPath = Files.createDirectories(Paths.get("ml/linear-regression/charts/"));

    public LinearRegression() throws IOException {

    }

    public static void main(String[] args) throws IOException {
        new LinearRegression().solve();
    }

    private void solve() throws IOException {
        var data = Table
                .read()
                .csv(
                        CsvReadOptions
                                .builder(LinearRegression.class.getClassLoader().getResourceAsStream("advertising.csv"))
                                .header(true)
                                .separator(',')
                );
        logger.info(data.shape());
        logger.info(data.structure().toString());
        logger.info(data.first(10).toString());

        data.numericColumns("TV", "Radio", "Newspaper").forEach(col -> {
            var stats = col.stats();
            logger.info("Column: {}, std: {}, mean: {}", col.name(), stats.standardDeviation(), stats.mean());
            var col2 = col.asDoubleColumn().map(v -> (v - stats.mean()) / stats.standardDeviation());
            data.replaceColumn(col.name(), col2);
        });

        logger.info("\n{}", data.first(10));

        // Now we have variables for TV, Radio, Newspaper and we want to figure out how sales depends on them.
        // This problem can be split into several small problems
        // First we need to measure if we have best result or not. So we need error function
        // Second we need some function that can predict sales depending on 3 params TV, Radio, Newspaper

        // In this article I'll use Polynomial approximation to build a prediction function (link to previous article)
        // y = w0 + w1 * x1 + w2 * x2^2 + w3 * x3^3 where y = sales, x1,x2,x3 - params (TV, Radio, Newspaper)
        // So we need to find best w0, w1, w2, w3 coefficients
        // To do that, I'll make 2 matrices,
        // first will be (data.size, 4) - will contain coefficients for w0, w1, w2, w3
        // and the second (data.size, 1) - will contain sales results

        data.addColumns(DoubleColumn.create("w0", data.rowCount()).fillWith(1));
        logger.info("Adding w0 coefficient to dataset\n{}", data.first(10));

        double[][] xData = data.as().doubleMatrix("w0", "TV", "Radio", "Newspaper");
        logger.info("Matrix X shape: ({}, {})", xData.length, xData[0].length);

        double[] yData = data.doubleColumn("Sales").asDoubleArray();
        logger.info("Matrix Y shape: ({}, 1)", yData.length);

        // As error function I'll use average quadratic deviation.
        // See com.borischistov.ml.lr.LinearRegression.error

        // To find coefficients I'll use 2 methods. First using normal equation
        var dataMatrix = new Array2DRowRealMatrix(xData);
        var solver = new QRDecomposition(dataMatrix).getSolver();
        var measure = measureTime(() -> solver.solve(new ArrayRealVector(yData)));

        logger.info(
                "Error: {}, results: {}, exec time: {} ms",
                error(yData, dataMatrix.operate(measure.result).toArray()),
                Arrays.toString(measure.result.toArray()),
                measure.execTime.toMillis()
        );

        var sgdSolver = new SGDSolver(dataMatrix, new ArrayRealVector(yData));
        var sgdMeasure = measureTime(() -> sgdSolver.solve(
                new ArrayRealVector(dataMatrix.getColumnDimension()),
                (currentCoefficients, dataRow, expectedValue) -> dataRow.dotProduct(currentCoefficients) - expectedValue
        ));
        logger.info(
                "SGD Error: {}, results: {}, exec time: {} ms, iterations: {}",
                error(yData, dataMatrix.operate(sgdMeasure.result.result).toArray()),
                Arrays.toString(sgdMeasure.result.result.toArray()),
                sgdMeasure.execTime.toMillis(),
                sgdMeasure.result.iterations
        );
    }

    private <T> Measure<T> measureTime(Supplier<T> call) {
        var start = Instant.now();
        return new Measure<>(call.get(), Duration.between(start, Instant.now()));
    }

    // Average quadratic deviation
    private double error(double[] expected, double[] predicted) {
        var res = 0d;
        for (var idx = 0; idx < expected.length; idx++) {
            res += Math.pow(predicted[idx] - expected[idx], 2);
        }
        return res / expected.length;
    }

    private static class Measure<T> {
        private final T result;
        private final Duration execTime;

        public Measure(T result, Duration execTime) {
            this.result = result;
            this.execTime = execTime;
        }
    }

    private static class SGDSolver {
        private final RealMatrix data;
        private final RealVector expected;
        private final double minWeightDistance;
        private final long maxIterations;
        private final double learningRate;
        private final Random random;

        public SGDSolver(RealMatrix data, RealVector expected) {
            this.data = data;
            this.expected = expected;
            this.maxIterations = 1_000_00;
            this.minWeightDistance = 0.00000001;
            this.random = new Random(42);
            this.learningRate = 0.01;
        }

        public SGDResult solve(
                RealVector initGuess,
                SGDStepFunction stepFunction
        ) {
            var weight = Double.POSITIVE_INFINITY;
            var coeffVect = initGuess;
            var currentIteration = 0L;

            while (weight > minWeightDistance && currentIteration < maxIterations) {
                var currentIndex = random.nextInt(data.getRowDimension());
                var dataRow = data.getRowVector(currentIndex);
                var expectedValue = expected.getEntry(currentIndex);
                var diff = stepFunction.next(
                        coeffVect,
                        dataRow,
                        expectedValue
                );
                var nextCoeffVect = coeffVect.subtract(dataRow.mapMultiply(diff * 2 * learningRate / data.getRowDimension()));
                weight = coeffVect.subtract(nextCoeffVect).getNorm();
                coeffVect = nextCoeffVect;
                currentIteration++;
            }
            return new SGDResult(currentIteration, coeffVect);
        }

        public static class SGDResult {
            private final long iterations;
            private final RealVector result;

            public SGDResult(long iterations, RealVector result) {
                this.iterations = iterations;
                this.result = result;
            }
        }

        public interface SGDStepFunction {
            double next(
                    RealVector currentCoefficients,
                    RealVector dataRow,
                    double expectedValue
            );
        }
    }

}
