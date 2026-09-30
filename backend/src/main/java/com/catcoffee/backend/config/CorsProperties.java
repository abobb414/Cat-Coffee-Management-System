package com.catcoffee.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 允许的前端来源，多个用英文逗号分隔。
     *
     * <p>本地开发时 {@code http://localhost:5173} 与 {@code http://127.0.0.1:5173}
     * 都会被浏览器按地址栏实际输入的主机名放进 Origin 请求头，两者是不同的来源。
     * 只配置其中一个，另一种写法就会在登录时被拦下并返回
     * {@code 403 Invalid CORS request}（前端表现为「当前账号没有操作权限」）。
     */
    private String allowedOrigin;

    /**
     * 解析出允许来源列表，兼容逗号分隔的多来源写法。
     */
    public List<String> resolvedOrigins() {
        if (!StringUtils.hasText(allowedOrigin)) {
            return List.of("http://localhost:5173");
        }
        return Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
