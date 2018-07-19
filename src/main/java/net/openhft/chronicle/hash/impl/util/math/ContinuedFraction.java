/*
 *      Copyright (C) 2015  higherfrequencytrading.com
 *      Copyright 2001-2015 The Apache Software Foundation
 *      Copyright 2010-2012 CS Systèmes d'Information
 *
 *      This program is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU Lesser General Public License as published by
 *      the Free Software Foundation, either version 3 of the License.
 *
 *      This program is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public License
 *      along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.hash.impl.util.math;

/**
 * Provides a generic means to evaluate continued fractions.  Subclasses simply
 * provided the a and b coefficients to evaluate the continued fraction.
 * <p>
 * <p>
 * References:
 * <ul>
 * <li><a href="http://mathworld.wolfram.com/ContinuedFraction.html">
 * Continued Fraction</a></li>
 * </ul>
 * </p>
 */
abstract class ContinuedFraction {

    /**
     * Access the n-th a coefficient of the continued fraction.  Since a can be
     * a function of the evaluation point, x, that is passed in as well.
     *
     * @param n the coefficient index to retrieve.
     * @param x the evaluation point.
     * @return the n-th a coefficient.
     */
    protected abstract double getA(int n, double x);

    /**
     * Access the n-th b coefficient of the continued fraction.  Since b can be
     * a function of the evaluation point, x, that is passed in as well.
     *
     * @param n the coefficient index to retrieve.
     * @param x the evaluation point.
     * @return the n-th b coefficient.
     */
    protected abstract double getB(int n, double x);

    /**
     * Evaluates the continued fraction at the value x.
     * <p>
     * The implementation of this method is based on the modified Lentz algorithm as described
     * on page 18 ff. in:
     * <ul>
     * <li>
     * I. J. Thompson,  A. R. Barnett. "Coulomb and Bessel Functions of Complex Arguments and Order."
     * <a target="_blank" href="http://www.fresco.org.uk/papers/Thompson-JCP64p490.pdf">
     * http://www.fresco.org.uk/papers/Thompson-JCP64p490.pdf</a>
     * </li>
     * </ul>
     * <b>Note:</b> the implementation uses the terms a<sub>i</sub> and b<sub>i</sub> as defined in
     * <a href="http://mathworld.wolfram.com/ContinuedFraction.html">Continued Fraction @ MathWorld</a>.
     * </p>
     *
     * @param x             the evaluation point.
     * @param epsilon       maximum error allowed.
     * @param maxIterations maximum number of convergents
     * @return the value of the continued fraction evaluated at x.
     * @throws IllegalStateException if the algorithm fails to converge.
     * @throws IllegalStateException if maximal number of iterations is reached
     */
    public double evaluate(double x, double epsilon, int maxIterations) {
        final double small = 1e-50;
        double hPrev = getA(0, x);

        // use the value of small as epsilon criteria for zero checks
        if (Precision.isEquals(hPrev, 0.0, small)) {
            hPrev = small;
        }

        int n = 1;
        double dPrev = 0.0;
        double cPrev = hPrev;
        double hN = hPrev;

        while (n < maxIterations) {
            final double a = getA(n, x);
            final double b = getB(n, x);

            double dN = a + b * dPrev;
            if (Precision.isEquals(dN, 0.0, small)) {
                dN = small;
            }
            double cN = a + b / cPrev;
            if (Precision.isEquals(cN, 0.0, small)) {
                cN = small;
            }

            dN = 1 / dN;
            final double deltaN = cN * dN;
            hN = hPrev * deltaN;

            if (Double.isInfinite(hN)) {
                throw new IllegalStateException(
                        "Continued fraction convergents diverged to +/- infinity for value " + x);
            }
            if (Double.isNaN(hN)) {
                throw new IllegalStateException(
                        "Continued fraction diverged to NaN for value " + x);
            }

            if (Math.abs(deltaN - 1.0) < epsilon) {
                break;
            }

            dPrev = dN;
            cPrev = cN;
            hPrev = hN;
            n++;
        }

        if (n >= maxIterations) {
            throw new IllegalStateException(
                    "Continued fraction convergents failed to converge (in less than " +
                            maxIterations + " iterations) for value " + x);
        }

        return hN;
    }

}
