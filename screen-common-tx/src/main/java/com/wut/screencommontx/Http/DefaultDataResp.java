package com.wut.screencommontx.Http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DefaultDataResp {
    private int code;
    private boolean flag;
    private String info;
    private Object data;
}
