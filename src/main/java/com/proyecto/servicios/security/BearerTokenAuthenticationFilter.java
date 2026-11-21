package com.proyecto.servicios.security;

import com.proyecto.servicios.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_ERROR_MESSAGE = "AUTH_ERROR_MESSAGE";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_STATUS_PATH = "/api/v1/auth/session-status";

    private final AuthService authService;

    public BearerTokenAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SESSION_STATUS_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<String> usuarioOpt = authService.validarTokenYObtenerUsuario(token);
                if (usuarioOpt.isPresent()) {
                    String username = usuarioOpt.get();
                    List<SimpleGrantedAuthority> authorities = "admin".equalsIgnoreCase(username.trim())
                            ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    token,
                                    authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (authService.fueDesplazadoPorOtroDispositivo(token)) {
                    request.setAttribute(
                            ATTR_AUTH_ERROR_MESSAGE,
                            "Se inició sesión con tu cuenta desde otro dispositivo. Por seguridad, esta sesión fue cerrada.");
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
