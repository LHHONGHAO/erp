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
                      value = "/jshERP-boot/platformConfig/getPlatform#/jshERP-boot/v2/api-docs#/jshERP-boot/webjars#" +
                              "/jshERP-boot/systemConfig/static#/jshERP-boot/api/plugin/wechat/weChat/share#" +
                              "/jshERP-boot/api/plugin/general-ledger/pdf/voucher#/jshERP-boot/api/plugin/tenant-statistics/tenantClean")})
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
        if (requestUrl.equals("/jshERP-boot/doc.html") || requestUrl.equals("/jshERP-boot/user/login")
                || requestUrl.equals("/jshERP-boot/user/register") || requestUrl.equals("/jshERP-boot/user/weixinLogin")
                || requestUrl.equals("/jshERP-boot/user/weixinBind") || requestUrl.equals("/jshERP-boot/user/registerUser")
                || requestUrl.equals("/jshERP-boot/user/randomImage")) {
            chain.doFilter(request, response);
            return;
        }
        if (null != allowUrls && allowUrls.length > 0) {
            for (String url : allowUrls) {
                if (requestUrl.startsWith(url)) {
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
        if(!requestUrl.equals("/jshERP-boot/user/logout") && !requestUrl.equals("/jshERP-boot/function/findMenuByPNumber")) {
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