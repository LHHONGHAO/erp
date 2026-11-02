package com.jsh.erp.filter;

import com.jsh.erp.service.RedisService;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(filterName = "LogCostFilter", urlPatterns = {"/*"},
        initParams = {@WebInitParam(name = "filterPath",
                      value = "/platformConfig/getPlatform#/v2/api-docs#/webjars#" +
                              "/systemConfig/static#/api/plugin/wechat/weChat/share#" +
                              "/api/plugin/general-ledger/pdf/voucher#/api/plugin/tenant-statistics/tenantClean#" +
                              "/cert")})
public class LogCostFilter implements Filter {

    private static final String FILTER_PATH = "filterPath";

    private String[] allowUrls;
    @Resource
    private RedisService redisService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String filterPath = filterConfig.getInitParameter(FILTER_PATH);
        if (!StringUtils.isEmpty(filterPath)) {
            allowUrls = filterPath.contains("#") ? filterPath.split("#") : new String[]{filterPath};
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest servletRequest = (HttpServletRequest) request;
        HttpServletResponse servletResponse = (HttpServletResponse) response;
        String requestUrl = servletRequest.getRequestURI();
        String contextPath = servletRequest.getContextPath();
        String path = requestUrl.startsWith(contextPath) ? requestUrl.substring(contextPath.length()) : requestUrl;
        if(requestUrl.contains("..") || requestUrl.contains("%2e") || requestUrl.contains("%2E")) {
            servletResponse.setStatus(500);
            servletResponse.getWriter().write("loginOut");
            return;
        }
        //具体，比如：处理若用户未登录，则跳转到登录页
        Object userId = redisService.getObjectFromSessionByKey(servletRequest,"userId");
        if(userId!=null) { //如果已登录，不阻止
            chain.doFilter(request, response);
            return;
        }
        if (path.equals("/doc.html") || path.equals("/user/login")
                || path.equals("/user/register") || path.equals("/user/weixinLogin")
                || path.equals("/user/weixinBind") || path.equals("/user/registerUser")
                || path.equals("/user/randomImage")) {
            chain.doFilter(request, response);
            return;
        }
        if (null != allowUrls && allowUrls.length > 0) {
            for (String url : allowUrls) {
                if (path.startsWith(url)) {
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        // 前后端合并部署：首页、静态资源、浏览器页面导航不参与登录校验
        if (isPublicResource(servletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        servletResponse.setStatus(500);
        if(!path.equals("/user/logout") && !path.equals("/function/findMenuByPNumber")) {
            servletResponse.getWriter().write("loginOut");
        }
    }

    /**
     * 是否为可直接访问的公共资源：站点根路径、带扩展名的静态文件、浏览器页面导航请求
     */
    private boolean isPublicResource(HttpServletRequest request) {
        String requestUrl = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestUrl.startsWith(contextPath) ? requestUrl.substring(contextPath.length()) : requestUrl;
        if (path.isEmpty() || "/".equals(path)) {
            return true;
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        if (lastSegment.contains(".")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    @Override
    public void destroy() {

    }
}