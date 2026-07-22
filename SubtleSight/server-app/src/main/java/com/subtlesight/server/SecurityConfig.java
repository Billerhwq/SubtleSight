package com.subtlesight.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${subtlesight.security.allowed-origins}")String origins)throws Exception{
        CookieCsrfTokenRepository csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();csrf.setCookieName("XSRF-TOKEN");csrf.setHeaderName("X-XSRF-TOKEN");
        http.authorizeHttpRequests(auth->auth.anyRequest().permitAll())
                .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterBefore(new OriginValidationFilter(origins),org.springframework.security.web.csrf.CsrfFilter.class)
                .formLogin(form->form.disable())
                .logout(logout->logout.disable());
        return http.build();
    }
    static final class OriginValidationFilter extends OncePerRequestFilter{
        private final Set<String> allowed;
        OriginValidationFilter(String csv){allowed=Arrays.stream(csv.split(",")).map(String::trim).filter(s->!s.isBlank()).collect(Collectors.toUnmodifiableSet());}
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{if(Set.of("POST","PUT","PATCH","DELETE").contains(request.getMethod())){String origin=request.getHeader("Origin");if(origin!=null&&!allowed.contains(normalize(origin))){response.sendError(403,"origin is not allowed");return;}}chain.doFilter(request,response);}
        private String normalize(String origin){try{URI u=URI.create(origin);int port=u.getPort();return u.getScheme()+"://"+u.getHost()+(port<0?"":":"+port);}catch(Exception e){return "invalid";}}
    }

    /** Uses XOR tokens for rendered request attributes and the plain cookie token for SPA headers. */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain=new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor=new XorCsrfTokenRequestAttributeHandler();
        @Override public void handle(HttpServletRequest request,HttpServletResponse response,Supplier<CsrfToken> token){xor.handle(request,response,token);token.get();}
        @Override public String resolveCsrfTokenValue(HttpServletRequest request,CsrfToken token){return (StringUtils.hasText(request.getHeader(token.getHeaderName()))?plain:xor).resolveCsrfTokenValue(request,token);}
    }
}
