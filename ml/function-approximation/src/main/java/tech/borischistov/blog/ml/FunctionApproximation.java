package tech.borischistov.blog.ml;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.internal.chartpart.Chart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class FunctionApproximation {

    private static final Path EXPORT_PATH = Paths.get("ml/graph-export/function-approximation/");

    public static void main(String[] args) throws IOException {
        double[][] matrix = new double[][]{
                new double[]{1, 1, 1, 1},
                new double[]{1, 4, 16, 256},
                new double[]{1, 8, 64, 512},
                new double[]{1, 15, 225, 3375}
        };

        double[] vector = new double[]{3.252216865271419, 1.7468459495903677, 2.5054164070002463, 0.6352214195786656};
        simpleMatrixSolveMethod(matrix, vector);

        var xValues = DoubleStream.iterate(1., (v) -> v < 15., (v) -> v + 0.5).toArray();
        var yValues = Arrays.stream(xValues).map(baseFunction()).toArray();
        var chart = new XYChartBuilder().xAxisTitle("X").yAxisTitle("Y").build();
        chart.addSeries("original function", xValues, yValues);
        yValues = Arrays.stream(xValues).map(producedFunction4Values()).toArray();
        chart.addSeries("4 variables approximation", xValues, yValues);
        exportPlot("function-approximation-1.png", chart);


        chart = new XYChartBuilder().xAxisTitle("X").yAxisTitle("Y").build();
        var xValued2V = new double[]{1, 15};
        var coefficients2V = solve(baseFunction(), xValued2V);
        var xValued3V = new double[]{1, 8, 15};
        var coefficients3V = solve(baseFunction(), xValued3V);
        var xValued4V = new double[]{1, 4, 10, 15};
        var coefficients4V = solve(baseFunction(), xValued4V);

        yValues = Arrays.stream(xValues).map(baseFunction()).toArray();
        chart.addSeries("original function", xValues, yValues);
        yValues = Arrays.stream(xValues).map(producedFunction(coefficients2V)).toArray();
        chart.addSeries("2 variables approximation", xValues, yValues);
        yValues = Arrays.stream(xValues).map(producedFunction(coefficients3V)).toArray();
        chart.addSeries("3 variables approximation", xValues, yValues);
        yValues = Arrays.stream(xValues).map(producedFunction(coefficients4V)).toArray();
        chart.addSeries("4 variables approximation", xValues, yValues);
        exportPlot("function-approximation-2.png", chart);
    }

    public static double[] solve(DoubleUnaryOperator function, double[] values) {
        var length = values.length;
        var matrix = MatrixUtils.createRealMatrix(
                Arrays
                .stream(values)
                .mapToObj(v -> IntStream.range(0, length).mapToDouble(scale -> Math.pow(v, scale)).toArray())
                .toArray(double[][]::new)
        );

        var vector = new ArrayRealVector(Arrays.stream(values).map(function).toArray());
        var solver = new LUDecomposition(matrix).getSolver();
        return solver.solve(vector).toArray();
    }

    private static DoubleUnaryOperator producedFunction4Values() {
        return (x) -> 3.757149595173763 - 0.5873771070708974 * x + 0.08652731571853979 * Math.pow(x, 2) -0.0040829385499861 * Math.pow(x, 3);
    }

    private static DoubleUnaryOperator producedFunction(double[] coefficients) {
        return (x) -> {
            var result = 0d;
            for(var scale = 0; scale < coefficients.length; scale++) {
                result += Math.pow(x, scale) * coefficients[scale];
            }
            return result;
        };
    }

    private static DoubleUnaryOperator baseFunction() {
        return (x) ->  Math.sin(x / 5.) * Math.exp(x / 10.) + 5. * Math.exp(-x / 2.);
    }

    private static void exportPlot(String fineName, Chart<?, ?> chart) throws IOException {
        if(Files.notExists(EXPORT_PATH)) Files.createDirectories(EXPORT_PATH);
        try(var out = Files.newOutputStream(EXPORT_PATH.resolve(fineName))) {
            BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG);
        }
    }

    private static double[] simpleMatrixSolveMethod(double[][] matrix, double[] values) {
        var m = new Array2DRowRealMatrix(matrix);
        var v = new ArrayRealVector(values);

        var solver = new LUDecomposition(m).getSolver();
        var result = solver.solve(v).toArray();
        System.out.println(Arrays.toString(result));
        return result;
    }
}
