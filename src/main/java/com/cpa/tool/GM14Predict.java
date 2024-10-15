package com.cpa.tool;
//
//import org.apache.commons.math3.linear.*;
//
//
//
//import org.apache.commons.math3.linear.Array2DRowRealMatrix;
//import org.apache.commons.math3.linear.DecompositionSolver;
//import org.apache.commons.math3.linear.LUDecomposition;
//import org.apache.commons.math3.linear.RealMatrix;
//import org.apache.commons.math3.linear.RealVector;
//import org.apache.commons.math3.linear.ArrayRealVector;
//
//import org.apache.commons.math3.linear.*;
//
//public class GM14Predict {
//
//    // Method to perform cumulative generation of the original data
//    private static double[] cumulativeGeneration(double[] data) {
//        double[] agodata = new double[data.length];
//        double cumulativeSum = 0;
//        for (int i = 0; i < data.length; i++) {
//            cumulativeSum += data[i];
//            agodata[i] = cumulativeSum;
//        }
//        return agodata;
//    }
//
//    public static double[] fitGM14Model(double[] mainData, double[] assocData1, double[] assocData2, double[] assocData3) {
//        double[] agomainData = cumulativeGeneration(mainData);
//        int n = agomainData.length;
//
//        double[][] B = new double[n - 1][4];
//        double[] Y = new double[n - 1];
//
//        for (int i = 1; i < n; i++) {
//            B[i - 1][0] = -0.5 * (agomainData[i - 1] + agomainData[i]);
//            B[i - 1][1] = 1;
//            B[i - 1][2] = assocData1[i] - assocData1[i - 1];
//            B[i - 1][3] = assocData2[i] - assocData2[i - 1];
////            Y[i - 1] = mainData[i] - mainData[i - 1];
//            Y[i - 1] = mainData[i];
//        }
//
//        // Use Apache Commons Math for stable linear system solving
//        RealMatrix matrixB = new Array2DRowRealMatrix(B);
//        RealVector vectorY = new ArrayRealVector(Y);
//
//        // Compute the pseudo-inverse of matrix B
//        DecompositionSolver solver = new SingularValueDecomposition(matrixB).getSolver();
//
//        RealVector coefficients = solver.solve(vectorY);
//        return coefficients.toArray();
//    }
//
//    public static void main(String[] args) {
//        // Sample data
//        double[] mainData = {10.0, 20.0, 30.0, 40.0, 50.0};
//        double[] assocData1 = {1.0, 2.0, 3.0, 4.0, 5.0};
//        double[] assocData2 = {5.0, 4.0, 3.0, 2.0, 1.0};
//        double[] assocData3 = {2.0, 3.0, 4.0, 5.0, 6.0};
//
//        // Fit the GM(1,4) model
//        double[] coefficients = fitGM14Model(mainData, assocData1, assocData2, assocData3);
//
//        // Print the coefficients
//        System.out.println("Model coefficients:");
//        for (int i = 0; i < coefficients.length; i++) {
//            System.out.println("b" + i + " = " + coefficients[i]);
//        }
//    }
//}

//import org.apache.commons.math3.linear.Array2DRowRealMatrix;
//import org.apache.commons.math3.linear.MatrixUtils;
//import org.apache.commons.math3.linear.RealMatrix;
//import org.apache.commons.math3.linear.RealMatrixPreservingVisitor;
//import org.apache.commons.math3.linear.RealVector;
//import org.apache.commons.math3.linear.SingularValueDecomposition;
//
//public class GM14Predict {
//
//    public static void main(String[] args) {
//        double[] x1 = {1.0, 2.0, 3.0, 4.0};
//        double[] x2 = {2.0, 3.0, 4.0, 5.0};
//        double[] x3 = {3.0, 4.0, 5.0, 6.0};
//        double[] x4 = {4.0, 5.0, 6.0, 7.0};
//
//        double[] result = gm14(x1, x2, x3, x4);
//
//        System.out.println("Predicted values:");
//        for (double v : result) {
//            System.out.println(v);
//        }
//    }
//
//    public static double[] gm14(double[] x1, double[] x2, double[] x3, double[] x4) {
//        int n = x1.length;
//
//        double[] y = new double[n];
//        for (int i = 1; i < n; i++) {
//            y[i] = x1[i] + x2[i] + x3[i] + x4[i];
//        }
//
//        double[] x1Cumulative = new double[n];
//        x1Cumulative[0] = x1[0];
//        for (int i = 1; i < n; i++) {
//            x1Cumulative[i] = x1Cumulative[i - 1] + x1[i];
//        }
//
//        RealMatrix B = new Array2DRowRealMatrix(n - 1, 5);
//        for (int i = 1; i < n; i++) {
//            B.setEntry(i - 1, 0, -0.5 * (x1Cumulative[i] + x1Cumulative[i - 1]));
//            B.setEntry(i - 1, 1, x2[i]);
//            B.setEntry(i - 1, 2, x3[i]);
//            B.setEntry(i - 1, 3, x4[i]);
//            B.setEntry(i - 1, 4, 1.0);
//        }
//
//        RealVector Y = MatrixUtils.createRealVector(y).getSubVector(1, n - 1);
//
//        SingularValueDecomposition svd = new SingularValueDecomposition(B);
//        RealVector params = svd.getSolver().solve(Y);
//
//        double a = params.getEntry(0);
//        double b = params.getEntry(1);
//        double c = params.getEntry(2);
//        double d = params.getEntry(3);
//        double e = params.getEntry(4);
//
//        double[] result = new double[n];
//        result[0] = x1[0];
//        for (int i = 1; i < n; i++) {
//            result[i] = (x1[0] - (b + c + d + e) / a) * Math.exp(-a * i) + (b + c + d + e) / a;
//        }
//
//        return result;
//    }
//

import org.apache.commons.math3.linear.*;

import java.util.Arrays;

public class GM14Predict {

    public static void main(String[] args) {
//        double[] x0 = {560823, 542386, 604834, 591248, 583031, 640636, 575688, 689637, 570790, 519574, 614677};
//        double[] x1 = {104, 101.8, 105.8, 111.5, 115.97, 120.03, 113.3, 116.4, 105.1, 83.4, 73.3};
//        double[] x2 = {135.6, 140.2, 140.1, 146.9, 144, 143, 133.3, 135.7, 125.8, 98.5, 99.8};
//        double[] x3 = {131.6, 135.5, 142.6, 143.2, 142.2, 138.4, 138.4, 135, 122.5, 87.2, 96.5};
        double[] x0 = {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
        double[] x1 = {0.28, 0.14, 0.09, 0.06, 0.06, 0.05, 0.04, 0.03, 0.03, 0.02, 0.02, 0.03, 0.03, 0.02, 0.02};
        double[] x2 = {62.75, 74.75, 13.0, 38.0, 96.0, 57.0, 62.75, 42.75, 13.0, 38.0, 18.75, 12.75, 18.0, 5.0, 33.0};
        double[] x3 = {0.03, 0.06, 0.1, 0.15, 0.15, 0.19, 0.32, 0.25, 0.26, 0.34, 0.28, 0.22, 0.25, 0.43, 0.42};
        double[] predict = gm14(x0, x1, x2, x3);

        System.out.println(Arrays.toString(predict));
    }

    public static double[] gm14(double[] x1, double[] x2, double[] x3, double[] x4) {

        double[] aAgo = AGO(x1);
        double[] x0Ago = AGO(x2);
        double[] x1Ago = AGO(x3);
        double[] x2Ago = AGO(x4);
//            double[] x3Ago = AGO(x3);

        double[][] xi = {x0Ago, x1Ago, x2Ago};

        double[] Z = JingLing(aAgo);

        double[] Y = Arrays.copyOfRange(x1, 1, x1.length);

        double[] B = new double[Z.length];
        for (int i = 0; i < Z.length; i++) {
            B[i] = -Z[i];
        }

        RealMatrix YMatrix = MatrixUtils.createColumnRealMatrix(Y);
        RealMatrix BMatrix = MatrixUtils.createRowRealMatrix(B).transpose();
        RealMatrix XMatrix = new Array2DRowRealMatrix(xi).getSubMatrix(0, xi.length - 1, 1, xi[0].length - 1).transpose();

        RealMatrix BMerged = MatrixUtils.createRealMatrix(BMatrix.getRowDimension(), BMatrix.getColumnDimension() + XMatrix.getColumnDimension());
        BMerged.setSubMatrix(BMatrix.getData(), 0, 0);
        BMerged.setSubMatrix(XMatrix.getData(), 0, 1);

        RealMatrix BTranspose = BMerged.transpose();
        RealMatrix theta = new SingularValueDecomposition(BTranspose.multiply(BMerged)).getSolver().solve(BTranspose.multiply(YMatrix));

        double al = theta.getEntry(0, 0);
        RealVector b = theta.getSubMatrix(1, theta.getRowDimension() - 1, 0, 0).getColumnVector(0);

        double[] U = new double[x1.length];
        for (int k = 0; k < x1.length; k++) {
            double sum = 0;
            for (int i = 0; i < 3; i++) {
                sum += b.getEntry(i) * xi[i][k];
            }
            U[k] = sum;
        }

        double[] F = new double[x1.length];
        F[0] = x1[0];
        for (int f = 1; f < x1.length; f++) {
            F[f] = (x1[0] - U[f - 1] / al) / Math.exp(al * f) + U[f - 1] / al;
        }

        double[] G = new double[x1.length];
        G[0] = x1[0];
        for (int g = 1; g < x1.length; g++) {
            G[g] = F[g] - F[g - 1];
        }

//        System.out.println("Predicted values:");
//        for (double v : G) {
//            System.out.println(v);
//        }

        return G;
    }


    public static double[] AGO(double[] m) {
        double[] mAgo = new double[m.length];
        mAgo[0] = m[0];
        for (int i = 1; i < m.length; i++) {
            mAgo[i] = mAgo[i - 1] + m[i];
        }
        return mAgo;
    }

    public static double[] JingLing(double[] m) {
        double[] Z = new double[m.length - 1];
        for (int j = 1; j < m.length; j++) {
            Z[j - 1] = (m[j] + m[j - 1]) / 2.0;
        }
        return Z;
    }
}