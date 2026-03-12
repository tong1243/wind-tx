package com.wut.screencommontx.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectDataModel {
    private String tableName;
    private double start;
    private double end;
}
