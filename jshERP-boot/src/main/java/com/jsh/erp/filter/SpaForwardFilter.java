package com.jsh.erp.filter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 前后端合并部署：Vue 使用 history 路由，刷新或直接访问页面地址时后端没有对应接口，
 * 这里把这类浏览器页面请求回退到 index.html，交给前端路由处理。
 * 只处理 GET + Accept 含 text/html + 路径无扩展名的请求，不影响接口调用和静态资源。
 */
@Component
public class SpaForwardFilter implements Filter {

    private final ObjectProvider<RequestMappingHandlerMapping> mappingProvider;

    public SpaForwardFilter(ObjectProvider<RequestMappingHandlerMapping> mappingProvider) {
        this.mappingProvider = mappingProvider;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // 无需初始化
    }

    @Override
    public void destroy() {
        // 无需释放资源
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String accept = req.getHeader("Accept");
        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String path = requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length()) : requestUri;

        // Safari（尤其是添加到主屏幕的页面）可能继续使用旧入口 HTML，
        // 即使 JS/CSS 文件名带 hash，入口也必须重新向服务器确认版本。
        boolean htmlNavigation = "GET".equalsIgnoreCase(req.getMethod())
                && accept != null && accept.contains("text/html");
        if (htmlNavigation || "/sw.js".equals(path)) {
            setNoCacheHeaders(res);
        } else if ("/platformConfig/getUiConfig".equals(path)) {
            // 地址、会员等打印模板是服务器共享配置，不能被浏览器或代理复用旧响应。
            setNoCacheHeaders(res);
        }
        if ("GET".equalsIgnoreCase(req.getMethod())
                && requestUri.indexOf('.') < 0
                && accept != null && accept.contains("text/html")) {
            if (isMobileEntry(requestUri)) {
                req.getRequestDispatcher("/mobile/index.html").forward(req, res);
                return;
            }
            RequestMappingHandlerMapping mapping = mappingProvider.getIfAvailable();
            if (mapping != null && !hasHandler(mapping, req)) {
                req.getRequestDispatcher("/index.html").forward(req, res);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
    }

    /**
     * 手机端为独立入口：/mobile、/mobile/ 及其页面地址统一回退到 /mobile/index.html
     */
    private boolean isMobileEntry(String uri) {
        return "/mobile".equals(uri) || uri.startsWith("/mobile/");
    }

    private boolean hasHandler(RequestMappingHandlerMapping mapping, HttpServletRequest req) {
        try {
            HandlerExecutionChain chain = mapping.getHandler(req);
            return chain != null;
        } catch (Exception e) {
            return false;
        }
    }
}
