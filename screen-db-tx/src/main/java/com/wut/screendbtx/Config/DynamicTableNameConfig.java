package com.wut.screendbtx.Config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.wut.screendbtx.Context.TableTimeContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.wut.screencommontx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;
import static com.wut.screencommontx.Static.DbModuleStatic.TABLE_SUFFIX_SEPARATOR;

@Configuration
public class DynamicTableNameConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
        dynamicTableNameInnerInterceptor.setTableNameHandler(
            (sql, tableName) -> {
                String timestamp = TableTimeContext.getTime(TABLE_SUFFIX_KEY);
                return tableName + TABLE_SUFFIX_SEPARATOR + timestamp;
            }
        );
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        return interceptor;
    }

}
