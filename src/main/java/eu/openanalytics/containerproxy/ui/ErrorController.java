package eu.openanalytics.containerproxy.ui;

import eu.openanalytics.containerproxy.api.BaseController;
import org.keycloak.adapters.OIDCAuthenticationError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.NestedServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import static eu.openanalytics.containerproxy.auth.impl.keycloak.AuthenticationFaillureHandler.SP_KEYCLOAK_ERROR_REASON;

@Controller
@RequestMapping("/error")
public class ErrorController extends BaseController implements org.springframework.boot.web.servlet.error.ErrorController {
  
  @RequestMapping(produces = "text/html")
  public String handleError(ModelMap map, HttpServletRequest request, HttpServletResponse response) {
    
    // handle keycloak errors
    Object obj = request.getSession().getAttribute(SP_KEYCLOAK_ERROR_REASON);
    if (obj instanceof OIDCAuthenticationError.Reason) {
      request.getSession().removeAttribute(SP_KEYCLOAK_ERROR_REASON);
      OIDCAuthenticationError.Reason reason = (OIDCAuthenticationError.Reason) obj;
      if (reason == OIDCAuthenticationError.Reason.INVALID_STATE_COOKIE ||
        reason == OIDCAuthenticationError.Reason.STALE_TOKEN) {
        // These errors are typically caused by users using wrong bookmarks (e.g. bookmarks with states in)
        // or when some cookies got stale. However, the user is logged into the IDP, therefore it's enough to
        // send the user to the main page and they will get logged in automatically.
        return "redirect:/";
      } else {
        return "redirect:/auth-error";
      }
    }
    
    Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
    if (exception == null) {
      exception = (Throwable) request.getAttribute("SPRING_SECURITY_LAST_EXCEPTION");
    }
    
    String[] msg = createMsgStack(exception);
    if (exception == null) {
      msg[0] = HttpStatus.valueOf(response.getStatus()).getReasonPhrase();
    }
    
    if (response.getStatus() == 200 && (exception != null) && isAccountStatusException(exception)) {
      return "redirect:/";
    }
    
    prepareMap(map);
    map.put("message", msg[0]);
    map.put("stackTrace", msg[1]);
    map.put("status", response.getStatus());
    
    return "error";
  }
  
  @RequestMapping(consumes = "application/json", produces = "application/json")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> error(HttpServletRequest request, HttpServletResponse response) {
    Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
    String[] msg = createMsgStack(exception);
    
    Map<String, Object> map = new HashMap<>();
    map.put("message", msg[0]);
    map.put("stackTrace", msg[1]);
    
    return new ResponseEntity<>(map, HttpStatus.valueOf(response.getStatus()));
  }
  
  public String getErrorPath() {
    return "/error";
  }
  
  private String[] createMsgStack(Throwable exception) {
    String message = "";
    String stackTrace = "";
    
    if (exception instanceof NestedServletException && exception.getCause() instanceof Exception) {
      exception = exception.getCause();
    }
    if (exception != null) {
      if (exception.getMessage() != null) message = exception.getMessage();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (PrintWriter writer = new PrintWriter(bos)) {
        exception.printStackTrace(writer);
      }
      stackTrace = bos.toString();
      stackTrace = stackTrace.replace(System.getProperty("line.separator"), "<br/>");
    }
    
    if (message == null || message.isEmpty()) message = "An unexpected server error occurred";
    if (stackTrace == null || stackTrace.isEmpty()) stackTrace = "n/a";
    
    return new String[]{message, stackTrace};
  }
  
  private boolean isAccountStatusException(Throwable exception) {
    if (exception instanceof AccountStatusException) return true;
    if (exception.getCause() != null) return isAccountStatusException(exception.getCause());
    return false;
  }
}
