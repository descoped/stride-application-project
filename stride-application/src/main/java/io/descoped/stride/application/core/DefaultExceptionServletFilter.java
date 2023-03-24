package io.descoped.stride.application.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DefaultExceptionServletFilter extends HttpFilter {

    static final Logger log = LoggerFactory.getLogger(DefaultExceptionServletFilter.class);

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain)
            throws IOException {
        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            log.error(String.format("While attempting to serve: %s %s",
                    request.getMethod(),
                    request.getRequestURI()), t);
            response.sendError(500);
        }
    }
}
