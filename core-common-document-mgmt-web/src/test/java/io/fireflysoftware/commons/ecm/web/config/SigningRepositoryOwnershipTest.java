package io.fireflysoftware.commons.ecm.web.config;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real repository candidate discovery from production metadata; does not launch Main or create beans. */
class SigningRepositoryOwnershipTest {
    @Test void signingRepositoriesCannotBeScannedOntoTheDefaultBusinessFactory()throws Exception {
        var loader=new DefaultResourceLoader(getClass().getClassLoader());
        var metadata=new CachingMetadataReaderFactory(loader).getMetadataReader("io.fireflysoftware.commons.ecm.web.EnterpriseContentManagementApplication").getAnnotationMetadata();
        var source=new AnnotationRepositoryConfigurationSource(metadata,EnableR2dbcRepositories.class,loader,new StandardEnvironment(),
                new SimpleBeanDefinitionRegistry(),AnnotationBeanNameGenerator.INSTANCE);
        Set<String> candidates=source.getCandidates(loader).stream().map(definition->definition.getBeanClassName()).collect(Collectors.toSet());
        assertTrue(candidates.contains("io.fireflysoftware.commons.ecm.models.repositories.DocumentRepository"));
        assertFalse(candidates.stream().anyMatch(name->name.startsWith("io.fireflysoftware.commons.ecm.models.repositories.signing.")),
                "Signing repositories are constructed only by their verified owner store graph");
    }
}
