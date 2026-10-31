package io.fireflysoftware.commons.ecm.core.signing;

import io.fireflysoftware.commons.ecm.core.signing.impl.SpringDataSigningRuntimeFactory;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeAccountBinding;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.InfrastructureProxy;
import org.springframework.r2dbc.connection.DelegatingConnectionFactory;
import org.springframework.r2dbc.connection.SingleConnectionFactory;
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Genuine Spring Data/owner construction; no PostgreSQL connection or provider exchange is attempted. */
class SigningRuntimeFactoryTest {
    static final class Fixture {
        final AtomicInteger acquisitions=new AtomicInteger();
        final ConnectionFactory driver;
        final GeneratedConfigSigningTenantBindingResolverTest.Fixture transport=new GeneratedConfigSigningTenantBindingResolverTest.Fixture();
        final OwnedLeaseConnectionFactory factory;
        final DatabaseClient client;final R2dbcEntityTemplate template;final R2dbcTransactionManager manager;final BoundedR2dbcResources resources;
        Fixture(){this(null);}
        Fixture(ConnectionFactory supplied){
            driver=supplied==null?mock(ConnectionFactory.class):supplied;
            if(supplied==null){when(driver.getMetadata()).thenReturn(()->"PostgreSQL");when(driver.create()).thenAnswer(call->{acquisitions.incrementAndGet();return Mono.error(new AssertionError("Composition acquired a business connection"));});}
            factory=new OwnedLeaseConnectionFactory(driver);client=DatabaseClient.builder().connectionFactory(factory).bindMarkers(PostgresDialect.INSTANCE.getBindMarkersFactory()).build();template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);manager=new R2dbcTransactionManager(factory);resources=BoundedR2dbcResources.verify(factory,client,template,manager);
        }
        SpringDataSigningRuntimeFactory assembly(DatabaseClient selected){return new SpringDataSigningRuntimeFactory(resources,selected,template,manager,
                new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),()->Mono.empty(),(principal,action,resource,facts)->Mono.empty(),
                transport.manager,transport.configClient,GeneratedConfigSigningTenantBindingResolverTest.CONFIG,"configuration-current",
                GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM,transport.clock,System::nanoTime);}
    }
    @Test void actualStoreAndWorkflowComposeWithoutAcquiringUntilAuthorityExists(){
        var f=new Fixture();var envelopes=mock(SignatureEnvelopePort.class);var content=mock(DocumentContentPort.class);var account=CurrentSigningPreparationPolicyTest.account();
        when(envelopes.accountBinding()).thenReturn(Mono.just(account.descriptor()));
        var workflow=f.assembly(f.client).create(account,content,envelopes).block(Duration.ofSeconds(3));assertNotNull(workflow);
        assertThrows(SecurityException.class,()->workflow.prepare(CurrentSigningPreparationPolicyTest.intent()).block(Duration.ofSeconds(3)));
        assertEquals(0,f.acquisitions.get());assertEquals(0,f.transport.sdkCalls.get());verifyNoInteractions(content);verify(envelopes,times(1)).accountBinding();
        assertThrows(SigningIntegrationUnavailableException.class,()->workflow.dispatch(null).block());
    }
    @Test void missingDefaultAdapterCapabilityRefusesBeforeBusinessConstruction(){
        var f=new Fixture();var envelopes=mock(SignatureEnvelopePort.class,CALLS_REAL_METHODS);var content=mock(DocumentContentPort.class);
        assertThrows(SigningIntegrationUnavailableException.class,()->f.assembly(f.client).create(CurrentSigningPreparationPolicyTest.account(),content,envelopes).block(Duration.ofSeconds(3)));
        assertEquals(0,f.acquisitions.get());verifyNoInteractions(content);
    }
    @Test void mismatchedServerAccountSnapshotCannotCreateAWorkflow(){
        var f=new Fixture();var envelopes=mock(SignatureEnvelopePort.class);var content=mock(DocumentContentPort.class);
        when(envelopes.accountBinding()).thenReturn(Mono.just(new SignatureEnvelopeAccountBinding(SignatureProvider.DOCUSIGN,"foreign-account","https://docusign.example.test/restapi")));
        assertThrows(SigningIntegrationUnavailableException.class,()->f.assembly(f.client).create(CurrentSigningPreparationPolicyTest.account(),content,envelopes).block(Duration.ofSeconds(3)));
        assertEquals(0,f.acquisitions.get());verifyNoInteractions(content);
    }
    @Test void mixedFactoryGraphRefusesBeforeAccountOrDatabaseIo(){
        var f=new Fixture();var other=mock(ConnectionFactory.class);when(other.getMetadata()).thenReturn(()->"PostgreSQL");
        assertThrows(IllegalArgumentException.class,()->f.assembly(DatabaseClient.create(other)));
        assertEquals(0,f.acquisitions.get());verify(other,never()).create();
    }
    static final class InfrastructureFactory extends DelegatingConnectionFactory implements InfrastructureProxy {
        InfrastructureFactory(ConnectionFactory target){super(target);}
        @Override public Object getWrappedObject(){return getTargetConnectionFactory();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualManagedScopedFactoryRefusesBeforeAnyTargetLookup(boolean infrastructureWrapper){
        var targetLookups=new AtomicInteger();var target=mock(ConnectionFactory.class);var beans=new DefaultListableBeanFactory();
        var definition=new RootBeanDefinition(ConnectionFactory.class);definition.setScope("prototype");
        definition.setInstanceSupplier(()->{targetLookups.incrementAndGet();return target;});beans.registerBeanDefinition("ownedScopedTarget",definition);
        var proxy=new ScopedProxyFactoryBean();proxy.setTargetBeanName("ownedScopedTarget");proxy.setProxyTargetClass(false);proxy.setBeanFactory(beans);
        var scoped=(ConnectionFactory)proxy.getObject();assertNotNull(scoped);
        var f=new Fixture(infrastructureWrapper?new InfrastructureFactory(scoped):scoped);
        assertThrows(IllegalArgumentException.class,()->f.assembly(f.client));assertEquals(0,targetLookups.get());verify(target,never()).create();beans.destroySingletons();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void transactionAwareOrSingleConnectionWrappersCannotBecomeAnOwnedIndependentGraph(boolean single){
        var target=mock(ConnectionFactory.class);var f=new Fixture(single?new SingleConnectionFactory(mock(io.r2dbc.spi.Connection.class),()->"PostgreSQL",true):new TransactionAwareConnectionFactoryProxy(target));
        assertThrows(IllegalArgumentException.class,()->f.assembly(f.client));verify(target,never()).create();
    }
    @Test void ordinaryInfrastructureFactoryRemainsSupportedWithoutAcquisition(){
        var target=mock(ConnectionFactory.class);var f=new Fixture(new InfrastructureFactory(target));
        assertNotNull(f.assembly(f.client));verify(target,never()).create();
    }

}
