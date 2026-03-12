package com.wut.screencommontx.Http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DefaultMsgResp {
    private boolean flag;
    private int code;
    private String info;
}
