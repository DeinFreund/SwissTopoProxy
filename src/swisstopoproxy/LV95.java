/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package swisstopoproxy;

import java.awt.geom.Point2D;

/**
 *
 * @author User
 */
public class LV95 {

    final static double ee = Math.sqrt(0.006674372230614);
    final static double lam0 = Math.PI * (7 + 26 / 60d + 22.5 / 3600d) / 180d;
    final static double rr = 6378815.90365;
    final static double alfa = 1.00072913843038;
    final static double b0 = Math.PI * (46 + 54 / 60d + 27.83324844 / 3600d) / 180d;
    final static double kk = 0.0030667323772751;

    // | Convert World coordiantes to Swiss coordinates LV95
    // returns horizontal, vertical
    static public Point2D.Double wgs2chHP(double lat, double lon) {
        double phi = lat * Math.PI / 180d;
        double lam = lon * Math.PI / 180d;

        double ss = alfa * Math.log(Math.tan(Math.PI / 4 + phi / 2)) - alfa * ee / 2 * Math.log((1 + ee * Math.sin(phi)) / (1 - ee * Math.sin(phi))) + kk;
        double b = 2 * (Math.atan(Math.exp(ss)) - Math.PI / 4);
        double l = alfa * (lam - lam0);

        double l2 = Math.atan(Math.sin(l) / (Math.sin(b0) * Math.tan(b) + Math.cos(b0) * Math.cos(l)));
        double b2 = Math.asin(Math.cos(b0) * Math.sin(b) - Math.sin(b0) * Math.cos(b) * Math.cos(l));
        double yy = rr * l2;
        double xx = rr / 2 * Math.log((1 + Math.sin(b2)) / (1 - Math.sin(b2)));
        double yy2 = yy + 2600000;
        double xx2 = xx + 1200000;
        return new Point2D.Double(yy2, xx2);
    }
}
