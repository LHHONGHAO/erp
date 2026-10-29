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
        if ("GET".equalsIgnoreCase(req.getMethod())
                && req.getRequestURI().indexOf('.') < 0
                && accept != null && accept.contains("text/html")) {
            RequestMappingHandlerMapping mapping = mappingProvider.getIfAvailable();
            if (mapping != null && !hasHandler(mapping, req)) {
                req.getRequestDispatcher("/index.html").forward(req, res);
                return;
            }
        }
        chain.doFilter(request, response);
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
