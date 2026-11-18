package io.descoped.stride.application.openapi;

import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lets an operation say that it needs no token in an application that authenticates. The document then carries a
 * security requirement for every operation; an operation annotated with
 * {@code @SecurityRequirement(name = "none")} gets an empty requirement instead, which OpenAPI reads as "no
 * authentication". Use it on the operations behind the paths listed in {@code security.public-paths}. The
 * annotation changes the document only: it does not open the path, the security module's setting does.
 */
public class ApplicationOpenApiSpecFilter extends AbstractSpecFilter {

    /** The name to give {@code @SecurityRequirement} on an operation that needs no authentication. */
    public static final String NO_AUTHENTICATION = "none";

    @Override
    public Optional<Operation> filterOperation(
            Operation operation, ApiDescription apiDescription,
            Map<String, List<String>> map, Map<String, String> map1, Map<String, List<String>> map2) {
        if (operation.getSecurity() != null && operation.getSecurity().size() == 1) {
            SecurityRequirement securityRequirement = operation.getSecurity().get(0);
            if (securityRequirement.containsKey(NO_AUTHENTICATION)) {
                return Optional.of(operation.security(List.of(new SecurityRequirement())));
            }
        }
        return Optional.of(operation);
    }
}
