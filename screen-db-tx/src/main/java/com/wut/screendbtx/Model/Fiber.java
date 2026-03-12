package com.wut.screendbtx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 光纤数据模型
@TableName("fiber")
public class Fiber {
    private String code;
    private String ip;                  // 雷达IP
    private Integer id;                 // 目标ID(雷达或者光纤给车分配的编号)
    private String type;                // 目标类型
    private Integer length;             // 目标长度
    private Integer width;              // 目标宽度
    @TableField("posx")
    @JsonProperty("posX")
    private Integer posX;               // X坐标
    @TableField("posy")
    @JsonProperty("posY")
    private Integer posY;               // Y坐标
    @TableField("speedx")
    @JsonProperty("speedX")
    private Double speedX;              // X轴速度
    @TableField("speedy")
    @JsonProperty("speedY")
    private Integer speedY;             // Y轴速度
    private Double speed;               // 车速
    private Integer acceleration;       // 目标方向加速度
    private Double longitude;           // 目标经度
    private Double latitude;            // 目标纬度
    @TableField("mercatorx")
    @JsonProperty("mercatorX")
    private Double mercatorX;           // 目标平面坐标X
    @TableField("mercatory")
    @JsonProperty("mercatorY")
    private Double mercatorY;           // 目标平面坐标Y
    @TableField("frenetx")
    @JsonProperty("frenetX")
    private Integer frenetX;            // 目标frenet坐标X
    @TableField("frenety")
    @JsonProperty("frenetY")
    private Double frenetY;             // 目标frenet坐标Y
    @TableField("headingAngle")
    @JsonProperty("headingAngle")
    private Double headingAngle;        // 目标航向角
    @TableField("FiberX")
    @JsonProperty("fiberX")
    private Integer fiberX;
    private Integer lane;
    @TableField("FrenetAngle")
    @JsonProperty("frenetAngle")
    private Integer frenetAngle;
    @TableField("RoadDirect")
    @JsonProperty("roadDirect")
    private Integer roadDirect;
    @TableField("carId")
    @JsonProperty("carId")
    private Integer carId;                  // 车牌照信息
    @TableField("timeStamp")
    private Double timestamp;
    @TableField("savetimestamp")
    @JsonProperty("saveTimestamp")
    private Double saveTimestamp;
    private String time;
    @TableField("savetime")
    @JsonProperty("saveTime")
    private String saveTime;
    @TableField("height")
    @JsonProperty("height")
    private Double height;
}
