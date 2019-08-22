package com.opencloud.gateway.zuul.server.filter;

import com.opencloud.base.client.model.entity.BaseApp;
import com.opencloud.common.constants.CommonConstants;
import com.opencloud.common.exception.OpenSignatureException;
import com.opencloud.common.model.ResultBody;
import com.opencloud.common.utils.SignatureUtils;
import com.opencloud.common.utils.WebUtils;
import com.opencloud.gateway.zuul.server.configuration.ApiProperties;
import com.opencloud.gateway.zuul.server.exception.JsonSignatureDeniedHandler;
import com.opencloud.gateway.zuul.server.service.feign.BaseAppServiceClient;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 数字验签前置过滤器
 *
 * @author: liuyadu
 * @date: 2018/11/28 18:26
 * @description:
 */
public class PreSignatureFilter extends OncePerRequestFilter {
    private JsonSignatureDeniedHandler signatureDeniedHandler;
    private BaseAppServiceClient baseAppServiceClient;
    private ApiProperties apiGatewayProperties;
    private static final AntPathMatcher pathMatch = new AntPathMatcher();

    public PreSignatureFilter(BaseAppServiceClient baseAppServiceClient, ApiProperties apiGatewayProperties,JsonSignatureDeniedHandler jsonSignatureDeniedHandler) {
        this.baseAppServiceClient = baseAppServiceClient;
        this.apiGatewayProperties = apiGatewayProperties;
        this.signatureDeniedHandler =  jsonSignatureDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        if (apiGatewayProperties.getCheckSign() && !notSign(requestPath)) {
            try {
                Map params = WebUtils.getParameterMap(request);
                // 验证请求参数
                SignatureUtils.validateParams(params);
                //开始验证签名
                if (baseAppServiceClient != null) {
                    String appId = params.get("appId").toString();
                    // 获取客户端信息
                    ResultBody<BaseApp> result = baseAppServiceClient.getApp(appId);
                    BaseApp app = result.getData();
                    if (app == null) {
                        throw new OpenSignatureException("appId无效");
                    }
                    // 强制覆盖请求参数clientId
                    params.put(CommonConstants.SIGN_APP_ID_KEY, app.getAppId());
                    // 服务器验证签名结果
                    if (!SignatureUtils.validateSign(params, app.getSecretKey())) {
                        throw new OpenSignatureException("签名验证失败!");
                    }
                }
            } catch (Exception ex) {
                signatureDeniedHandler.handle(request, response, ex);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    protected boolean notSign(String requestPath) {
        if(apiGatewayProperties.getSignIgnores()==null){
            return false;
        }
        for (String path : apiGatewayProperties.getSignIgnores()) {
            if (pathMatch.match(path, requestPath)) {
                return true;
            }
        }
        return false;
    }
}
