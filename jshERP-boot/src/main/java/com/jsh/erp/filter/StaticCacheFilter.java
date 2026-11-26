package com.jsh.erp.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 带哈希指纹的静态资源强缓存
 *
 * 背景：项目使用 Spring Boot 2.0（Spring 5.0），静态资源处理器的默认行为是：
 * 无缓存配置时无条件写 Cache-Control: no-cache（isEmpty 保护是 Spring 5.2 才加入的），
 * 导致浏览器每次打开页面都为已缓存文件逐个发验证请求（304），几十个请求在弱服务器
 * + 手机端上要排队数秒，直接拖慢进入首页。
 *
 * 实现：过滤器先写好强缓存头，再用响应包装器拦截后续对 Cache-Control 的覆盖写入
 * （Spring 5.0 的资源处理器会无条件覆盖，必须拦住）。带哈希的文件名内容不变则不变，
 * 一年强缓存无旧内容风险；发版后文件名更换，浏览器自动下载新版本。
 * 入口页的新鲜度由 SpaForwardFilter 显式 no-store 保证，不在本过滤器的路径范围内。
 */
// @Component 由 FilterBean 注册：Boot 自动把 Filter bean 注册到所有请求
@org.springframework.core.annotation.Order(1)
@org.springframework.stereotype.Component
public class StaticCacheFilter implements Filter {

    /** 带哈希指纹的静态资源路径：电脑版 js/css/img/fonts + 手机版 mobile 子目录 */
    private static final Pattern HASHED_PATH = Pattern.compile(
            "^/(?:js|css|img|fonts|mobile/(?:js|css|img|fonts|icons))/.*$");

    /** 哈希资源强缓存一年：内容不变文件名就不变 */
    private static final String HASHED_CACHE_CONTROL = "max-age=31536000, public";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;
            if (HASHED_PATH.matcher(req.getRequestURI()).matches()) {
                // 先写强缓存头；再拦住后面处理器对 Cache-Control 的覆盖（Spring 5.0 无条件写 no-cache）
                res.setHeader("Cache-Control", HASHED_CACHE_CONTROL);
                chain.doFilter(request, new javax.servlet.http.HttpServletResponseWrapper(res) {
                    @Override
                    public void setHeader(String name, String value) {
                        if ("Cache-Control".equalsIgnoreCase(name)) return;
                        super.setHeader(name, value);
                    }

                    @Override
                    public void addHeader(String name, String value) {
                        if ("Cache-Control".equalsIgnoreCase(name)) return;
                        super.addHeader(name, value);
                    }
                });
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void init(javax.servlet.FilterConfig filterConfig) {
        // 无初始化需求
    }

    @Override
    public void destroy() {
        // 无清理需求
    }
}
