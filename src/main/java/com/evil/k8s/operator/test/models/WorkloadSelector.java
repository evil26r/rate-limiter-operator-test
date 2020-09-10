package com.evil.k8s.operator.test.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@ToString
@EqualsAndHashCode
public class WorkloadSelector {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> labels;

    public WorkloadSelector() {
    }

    public WorkloadSelector(Map<String, String> labels) {
        super();
        this.labels = labels;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

}
