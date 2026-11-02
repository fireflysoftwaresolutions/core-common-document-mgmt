/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.config;

import io.fireflysoftware.commons.ecm.models.config.DocumentEvidenceMapping;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.extension.Extension;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="document.evidence.upload.enabled",havingValue="true")
public class DocumentEvidencePersistenceConfiguration {
    @Bean R2dbcCustomConversions documentEvidenceConversions(){return R2dbcCustomConversions.of(PostgresDialect.INSTANCE,DocumentEvidenceMapping.converters());}
    @Bean ConnectionFactoryOptionsBuilderCustomizer documentEvidenceEnumCodecs(){return options->{
        var extensions=new ArrayList<Extension>();Object configured=options.build().getValue(PostgresqlConnectionFactoryProvider.EXTENSIONS);
        if(configured!=null){
            if(!(configured instanceof Collection<?> collection))throw new IllegalStateException("Declared PostgreSQL driver extensions required");
            for(Object extension:collection){if(!(extension instanceof Extension typed))throw new IllegalStateException("Declared PostgreSQL driver extension required");extensions.add(typed);}
        }
        extensions.add(DocumentEvidenceMapping.codecs());options.option(PostgresqlConnectionFactoryProvider.EXTENSIONS,List.copyOf(extensions));
    };}
}
