package com.example.photoscore.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(this::customizeConnector);
    }

    private void customizeConnector(Connector connector) {
        // 1. 最大 POST 大小无限制
        connector.setMaxPostSize(-1);
        
        // 2. 连接超时设为 3 分钟（足够大文件上传）
        connector.setProperty("connectionTimeout", "180000");
        
        // 3. Keep-Alive 超时 2 分钟
        connector.setProperty("keepAliveTimeout", "120000");
        
        // 4. 【关键】允许的最大请求体大小，-1 表示不限制
        connector.setProperty("maxPostSize", "-1");
        
        // 5. 【关键】最大吞咽大小：当客户端提前关闭连接时，Tomcat 会尝试“吞掉”剩余数据，
        //    而不是立刻报 EOF。设置为 -1 表示吞掉所有剩余数据，避免异常。
        connector.setProperty("maxSwallowSize", "-1");
        
        // 6. 增大缓冲区，减少碎片化传输
        connector.setProperty("socketBuffer", "16384");
        
        // 7. 允许异步请求的超时时间
        connector.setAsyncTimeout(180000);
    }
}