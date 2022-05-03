package hk.edu.polyu.comp.vlabcontroller.security;

import hk.edu.polyu.comp.vlabcontroller.auth.IAuthenticationBackend;
import hk.edu.polyu.comp.vlabcontroller.auth.UserLogoutHandler;
import hk.edu.polyu.comp.vlabcontroller.config.ServerProperties;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

import static io.vavr.API.*;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    private final UserLogoutHandler logoutHandler;
    private final IAuthenticationBackend auth;
    private final AuthenticationEventPublisher eventPublisher;
    private final ServerProperties serverProperties;
    @Setter(onMethod_ = {@Autowired(required = false)})
    private List<ICustomSecurityConfig> customConfigs;

    @Override
    public void configure(WebSecurity web) {
        if (customConfigs != null) {
            for (var cfg : customConfigs) {
                try {
                    cfg.apply(web);
                } catch (Exception e) {
                    // This function may not throw exceptions, therefore we exit the process here
                    // We do not want half-configured security.
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Perform CSRF check on the login form
        http.csrf().requireCsrfProtectionMatcher(new AntPathRequestMatcher("/login", "POST"));

        // Always set header: X-Content-Type-Options=nosniff
        var headers = http.headers();
        var frameOptionsConfig = headers.frameOptions();
        headers.contentTypeOptions();

        var frameOptions = serverProperties.getFrameOptions();
        Match(frameOptions.toUpperCase()).of(
            Case($("DISABLE"), () -> run(frameOptionsConfig::disable)),
            Case($("DENY"), () -> run(frameOptionsConfig::deny)),
            Case($("SAMEORIGIN"), () -> run(frameOptionsConfig::sameOrigin)),
            Case($(), cappedFrameOptions -> run(() -> {
                if (cappedFrameOptions.startsWith("ALLOW-FROM")) {
                    frameOptionsConfig.disable().addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", frameOptions));
                }
            }))
        );

        // Allow public access to health endpoint
        http.authorizeRequests().antMatchers("/actuator/health").permitAll();
        http.authorizeRequests().antMatchers("/actuator/health/readiness").permitAll();
        http.authorizeRequests().antMatchers("/actuator/health/liveness").permitAll();
        http.authorizeRequests().antMatchers("/actuator/prometheus").permitAll();

        // Note: call early, before http.authorizeRequests().anyRequest().fullyAuthenticated();
        for (var cfg : Option.of(customConfigs).getOrElse(List.of())) cfg.apply(http);


        if (auth.hasAuthorization()) {
            http.authorizeRequests().antMatchers(
                    "/login", "/auth-error", "/app-access-denied", "/logout-success", "/error",
                    "/favicon.ico", "/assets/**").permitAll();
            http
                    .formLogin()
                    .loginPage("/login")
                    .and()
                    .logout()
                    .logoutUrl(auth.getLogoutURL())
                    // important: set the next option after logoutUrl because it would otherwise get overwritten
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .addLogoutHandler(logoutHandler)
                    .logoutSuccessUrl(auth.getLogoutSuccessURL());

            // Enable basic auth for RESTful calls when APISecurityConfig is not enabled.
            http.addFilter(new BasicAuthenticationFilter(super.authenticationManagerBean()));
        }


        if (auth.hasAuthorization()) {
            // The `anyRequest` method may only be called once.
            // Therefore we call it here, make our changes to it and forward it to the various authentication backends
            var anyRequestConfigurer = http.authorizeRequests().anyRequest();
            anyRequestConfigurer.fullyAuthenticated();
            auth.configureHttpSecurity(http, anyRequestConfigurer);
        }
    }

    @Bean
    public GlobalAuthenticationConfigurerAdapter authenticationConfiguration() {
        return new GlobalAuthenticationConfigurerAdapter() {
            @Override
            public void init(AuthenticationManagerBuilder amb) throws Exception {
                amb.authenticationEventPublisher(eventPublisher);
                auth.configureAuthenticationManagerBuilder(amb);
            }
        };
    }

    @Bean(name = "authenticationManager")
    @ConditionalOnExpression("'${proxy.authentication}' == 'kerberos' || '${proxy.authentication}' == 'saml' || '${proxy.authentication}' == 'keycloak'")
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

}