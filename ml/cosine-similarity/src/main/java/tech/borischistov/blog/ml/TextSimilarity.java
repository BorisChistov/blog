package tech.borischistov.blog.ml;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.text.similarity.CosineDistance;

public class TextSimilarity {

    public static void main(String[] args) {
        // Commons text solution
        var s1 = "A B C D A";
        var s2 = "A C C D G";
        var s3 = "B C G G A";

        var distance = new CosineDistance();

        System.out.println("Apache commons text result: ");
        System.out.println(distance.apply(s1, s2));
        System.out.println(distance.apply(s1, s3));
        System.out.println();
        // commons math solution - easy
        var v1 = new double[]{2., 1., 1., 1., 0.};
        var v2 = new double[]{1., 0., 2., 1., 1.};
        var v3 = new double[]{1., 1., 1., 0., 2.};

        var vect1 = new ArrayRealVector(v1);
        var vect2 = new ArrayRealVector(v2);
        var vect3 = new ArrayRealVector(v3);

        System.out.println("Apache commons maths result: ");
        System.out.println(1 - vect1.cosine(vect2));
        System.out.println(1 - vect1.cosine(vect3));
        System.out.println();

        System.out.println("Apache commons maths result with distance calculation: ");
        System.out.println(cosineDistance(vect1, vect2));
        System.out.println(cosineDistance(vect1, vect3));
        System.out.println();
    }

    private static double cosineDistance(RealVector v1, RealVector v2) {
        var dotProduct = v1.dotProduct(v2);
        var v1Norm = v1.getNorm();
        var v2Norm = v2.getNorm();

        return 1 - dotProduct / (v1Norm * v2Norm);
    }

}
