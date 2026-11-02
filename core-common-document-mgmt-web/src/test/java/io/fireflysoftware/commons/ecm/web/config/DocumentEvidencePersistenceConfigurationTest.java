/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.config;

import io.fireflysoftware.commons.ecm.models.config.DocumentEvidenceMapping;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import static org.assertj.core.api.Assertions.*;

class DocumentEvidencePersistenceConfigurationTest {
    @Test void disabledEvidenceCannotReplaceLegacyConversionOrConnectionConfiguration(){
        new ApplicationContextRunner().withUserConfiguration(DocumentEvidencePersistenceConfiguration.class).run(context->
            assertThat(context).hasNotFailed().doesNotHaveBean(ConnectionFactoryOptionsBuilderCustomizer.class).doesNotHaveBean(R2dbcCustomConversions.class));
    }
    @Test void enabledEvidencePreservesExistingDriverExtensionsAndSuppliesNativeEnumConversions(){
        new ApplicationContextRunner().withUserConfiguration(DocumentEvidencePersistenceConfiguration.class)
            .withPropertyValues("document.evidence.upload.enabled=true").run(context->{
                assertThat(context).hasNotFailed().hasSingleBean(ConnectionFactoryOptionsBuilderCustomizer.class).hasSingleBean(R2dbcCustomConversions.class);
                var retained=DocumentEvidenceMapping.codecs();
                var options=ConnectionFactoryOptions.builder().option(ConnectionFactoryOptions.DRIVER,"postgresql")
                    .option(PostgresqlConnectionFactoryProvider.EXTENSIONS,List.of(retained));
                context.getBean(ConnectionFactoryOptionsBuilderCustomizer.class).customize(options);
                var actual=(List<?>)options.build().getValue(PostgresqlConnectionFactoryProvider.EXTENSIONS);
                assertThat(actual).hasSize(2);assertThat(actual.getFirst()).isSameAs(retained);
            });
    }
}
