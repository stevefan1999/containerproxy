package eu.openanalytics.containerproxy.security;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.UserLogoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.inject.Inject;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
  
  @Inject
  private UserLogoutHandler logoutHandler;
  
  @Inject
  private IAuthenticationBackend auth;
  
  @Inject
  private AuthenticationEventPublisher eventPublisher;
  
  @Inject
  private Environment environment;
  
  @Autowired(required = false)
  private List<ICustomSecurityConfig> customConfigs;
  
  @Override
  public void configure(WebSecurity web) {
//		web
//			.ignoring().antMatchers("/css/**").and()
//			.ignoring().antMatchers("/img/**").and()
//			.ignoring().antMatchers("/js/**").and()
//			.ignoring().antMatchers("/assets/**").and()
//			.ignoring().antMatchers("/webjars/**").and();
//
    if (customConfigs != null) {
      for (ICustomSecurityConfig cfg : customConfigs) {
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
    http.headers().contentTypeOptions();
    
    String frameOptions = environment.getProperty("server.frameOptions", "disable");
    switch (frameOptions.toUpperCase()) {
      case "DISABLE":
        http.headers().frameOptions().disable();
        break;
      case "DENY":
        http.headers().frameOptions().deny();
        break;
      case "SAMEORIGIN":
        http.headers().frameOptions().sameOrigin();
        break;
      default:
        if (frameOptions.toUpperCase().startsWith("ALLOW-FROM")) {
          http.headers()
            .frameOptions().disable()
            .addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", frameOptions));
        }
    }
    
    // Allow public access to health endpoint
    http.authorizeRequests().antMatchers("/actuator/health").permitAll();
    http.authorizeRequests().antMatchers("/actuator/health/readiness").permitAll();
    http.authorizeRequests().antMatchers("/actuator/health/liveness").permitAll();
    http.authorizeRequests().antMatchers("/actuator/prometheus").permitAll();
    
    // Note: call early, before http.authorizeRequests().anyRequest().fullyAuthenticated();
    if (customConfigs != null) {
      for (ICustomSecurityConfig cfg : customConfigs) cfg.apply(http);
    }
    
    
    if (auth.hasAuthorization()) {
      http.authorizeRequests().antMatchers(
        "/login", "/signin/**", "/auth-error", "/app-access-denied", "/logout-success",
        "/favicon.ico", "/css/**", "/img/**", "/js/**", "/assets/**", "/webjars/**").permitAll();
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
      ExpressionUrlAuthorizationConfigurer<HttpSecurity>.AuthorizedUrl anyRequestConfigurer = http.authorizeRequests().anyRequest();
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
