package com.evil.k8s.operator.test.utils;

public class Utils {

    public static final String TCP = "TCP";
    public static final int redisPort = 6379;


    public static String generateMountPath(String runtimeRoot, String runtimeSubdirectory) {
        return runtimeRoot + "/" + runtimeSubdirectory + "/config";
    }

    public static String generateRedisName(String name) {
        return name + "-redis";
    }

}
