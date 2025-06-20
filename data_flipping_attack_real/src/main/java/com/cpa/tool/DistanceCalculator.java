package com.cpa.tool;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalPosition;

/**
 * 计算server和user的距离，保存到
 */
public class DistanceCalculator {
//    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
//        return Math.sqrt(Math.pow(lat2 - lat1, 2) + Math.pow(lng2 - lng1, 2));
//    }
//
//    public static double[][] generateServerUserDistanceMatrix(List<EdgeUser> users, List<EdgeServer> servers) {
//        int usersCount = users.size();
//        int serversCount = servers.size();
//        double[][] serverUserDistanceMatrix = new double[usersCount][serversCount];
//
//        for (int i = 0; i < usersCount; i++) {
//            EdgeUser user = users.get(i);
//            for (int j = 0; j < serversCount; j++) {
//                EdgeServer server = servers.get(j);
//                serverUserDistanceMatrix[i][j] = calculateDistance(user.lat, user.lng, server.lat, server.lng);
//            }
//        }
//
//        return serverUserDistanceMatrix;
//    }

    private static final double EARTH_RADIUS = 6371.0; // 地球半径，单位：千米

    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        GeodeticCalculator geoCalc = new GeodeticCalculator();

        Ellipsoid reference = Ellipsoid.WGS84;

        GlobalPosition serverPoint = new GlobalPosition(lat1, lng1, 0.0); // Point A

        GlobalPosition userPoint = new GlobalPosition(lat2, lng2, 0.0); // Point B

        double distance = geoCalc.calculateGeodeticCurve(reference, userPoint, serverPoint).getEllipsoidalDistance();

        return distance;
    }

}
