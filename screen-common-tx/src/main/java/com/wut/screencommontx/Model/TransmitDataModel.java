package com.wut.screencommontx.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransmitDataModel {
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("data")
    private Object data;
}
