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
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${subtlesight.security.allowed-origins}")String origins)throws Exception{
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        http.authorizeHttpRequests(auth->auth.anyRequest().permitAll())
                .csrf(c->c.csrfTokenRepository(csrf))
                .addFilterBefore(new OriginValidationFilter(origins),SecurityContextHolderFilter.class)
                .headers(headers->headers.frameOptions(fo->fo.sameOrigin()))
                .formLogin(form->form.disable())
                .logout(logout->logout.disable());
        return http.build();
    }
    static final class OriginValidationFilter extends OncePerRequestFilter{
        private final Set<String> allowed;
        private final boolean enabled;
        OriginValidationFilter(String csv){
            this.allowed=Arrays.stream(csv.split(",")).map(String::trim).filter(s->!s.isBlank()).collect(Collectors.toUnmodifiableSet());
            this.enabled=!this.allowed.isEmpty();
        }
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
            if(!enabled||!Set.of("POST","PUT","PATCH","DELETE").contains(request.getMethod())){chain.doFilter(request,response);return;}
            String origin=request.getHeader("Origin");
            if(origin!=null&&!allowed.contains(normalize(origin))){response.sendError(403,"origin is not allowed");return;}
            chain.doFilter(request,response);
        }
        private String normalize(String origin){try{URI u=URI.create(origin);int port=u.getPort();return u.getScheme()+"://"+u.getHost()+(port<0?"":":"+port);}catch(Exception e){return "invalid";}}
    }
}
