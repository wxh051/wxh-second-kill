package com.manster.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
//@EnableAutoConfiguration（把此类变成springboot的bean，并且可以开启基于springboot的自动化配置。）
@SpringBootApplication(scanBasePackages = {"com.manster.seckill"})//把根目录的包依次往下扫描，通过注解的方式自动发现特定注解
@MapperScan("com.manster.seckill.dao")  //Dao存放的地方设置在这个注解下
public class App {

    public static void main( String[] args ) {
        SpringApplication.run(App.class,args);
    }

}
