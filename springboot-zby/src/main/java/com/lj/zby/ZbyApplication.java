package com.lj.zby;

import com.lj.zby.listener.AppStartLisener;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@MapperScan("com.lj.zby.mapper")
public class ZbyApplication extends SpringBootServletInitializer{

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ZbyApplication.class);
		app.addListeners(new AppStartLisener());
		app.run();
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(ZbyApplication.class);
	}
}
