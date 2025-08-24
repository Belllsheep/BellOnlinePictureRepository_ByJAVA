package com.belllsheep.bellpicture.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Json序列化器配置
 * 将所有VO的Long类型属性序列化为String类型
 * 以解决js处理Long精度丢失的问题
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Json序列化器配置
     * <p>将所有VO的Long类型属性序列化为String类型</p>
     * @return
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizer() {
        return builder -> {
            // 将Long类型序列化为String类型
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}