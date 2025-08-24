package com.belllsheep.bellpicture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("com.belllsheep.bellpicture.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)  //使得业务逻辑中可以访问当前代理对象

public class BellPictureApplication {

    public static void main(String[] args) {
        SpringApplication.run(BellPictureApplication.class, args);
    }

}
