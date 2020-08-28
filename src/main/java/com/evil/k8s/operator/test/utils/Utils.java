package com.evil.k8s.operator.test.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class Utils {

    public static final String TCP = "TCP";
    public static final int redisPort = 6379;

    public static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());


    public static String generateMountPath(String runtimeRoot, String runtimeSubdirectory) {
        return runtimeRoot + "/" + runtimeSubdirectory + "/config";
    }

    public static String generateRedisName(String name) {
        return name + "-redis";
    }

}
