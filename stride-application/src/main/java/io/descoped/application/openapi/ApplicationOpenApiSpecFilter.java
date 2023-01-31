package io.descoped.application.openapi;

import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApplicationOpenApiSpecFilter extends AbstractSpecFilter {

    @Override
    public Optional<Operation> filterOperation(
            Operation operation, ApiDescription apiDescription,
            Map<String, List<String>> map, Map<String, String> map1, Map<String, List<String>> map2) {
        if (operation.getSecurity() != null && operation.getSecurity().size() == 1) {
            SecurityRequirement securityRequirement = operation.getSecurity().get(0);
            if (securityRequirement.containsKey("none")) {
                return Optional.of(operation.security(List.of(new SecurityRequirement())));
            }
        }
        return Optional.of(operation);
    }
}
