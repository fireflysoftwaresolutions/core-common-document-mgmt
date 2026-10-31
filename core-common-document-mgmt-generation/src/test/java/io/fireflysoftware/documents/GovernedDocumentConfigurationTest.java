package io.fireflysoftware.documents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.SpringDataDocumentGenerationCommitService;
import io.fireflysoftware.documents.render.GovernedFreeMarkerTest;
import static org.mockito.Mockito.mock;

class GovernedDocumentConfigurationTest {
    private ApplicationContextRunner runner(){return new ApplicationContextRunner().withUserConfiguration(GovernedDocumentConfiguration.class);}
    @Test void governedBranchIsAbsentByDefault(){runner().run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("governedDocumentPort"));});}
    @Test void enablingWithoutGenuineCapabilitiesRefusesStartup(){runner().withPropertyValues("firefly.documents.governed.enabled=true").run(c->assertNotNull(c.getStartupFailure()));}
    @Test void explicitDependenciesExposeOnlyGovernedPortWithoutLegacyFallback(){
        runner().withUserConfiguration(DocumentAdapterConfiguration.class).withPropertyValues("firefly.documents.governed.enabled=true")
            .withBean(DocumentCurrentSnapshotService.class,()->mock(DocumentCurrentSnapshotService.class))
            .withBean(DocumentRenderProfiles.class,()->s->GovernedFreeMarkerTest.profile(s))
            .withBean(DocumentGenerationEventSource.class,()->mock(DocumentGenerationEventSource.class))
            .withBean(SpringDataDocumentGenerationCommitService.class,()->mock(SpringDataDocumentGenerationCommitService.class))
            .withBean("documentGenerationClock",java.time.Clock.class,()->GovernedFreeMarkerTest.CLOCK)
            .withBean("documentGenerationNanos",java.util.function.LongSupplier.class,()->GovernedFreeMarkerTest.NANOS)
            .run(c->{assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(DocumentPort.class).size());
                assertTrue(c.containsBean("governedDocumentPort"));assertTrue(c.getBeansOfType(DocumentTemplateCatalog.class).isEmpty());
                assertTrue(c.getBeansOfType(io.fireflysoftware.documents.storage.DocumentStoragePort.class).isEmpty());});
    }
}
