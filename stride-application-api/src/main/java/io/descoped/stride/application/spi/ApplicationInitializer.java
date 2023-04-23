package io.descoped.stride.application.spi;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;

public interface ApplicationInitializer {

    StrideApplication initialize(ApplicationConfiguration configuration);

}
