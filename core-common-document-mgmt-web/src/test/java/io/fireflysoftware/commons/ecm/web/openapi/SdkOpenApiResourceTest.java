package io.fireflysoftware.commons.ecm.web.openapi;

import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.*;

class SdkOpenApiResourceTest {
    @Test
    void compatibilityResourceMirrorsOnlyTheOriginalProducerCanonicalInput() throws Exception {
        var module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var pom = factory.newDocumentBuilder().parse(module.resolve("../core-common-document-mgmt-sdk/pom.xml").normalize().toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        var executionPath = "/project/build/plugins/plugin[artifactId='maven-resources-plugin']/executions/execution[id='mirror-producer-openapi-compatibility-resource']";
        assertEquals(1.0, xpath.evaluate("count(" + executionPath + ")", pom, XPathConstants.NUMBER));
        var execution = (Node) xpath.evaluate(executionPath, pom, XPathConstants.NODE);
        assertEquals("process-resources", xpath.evaluate("phase", execution));
        assertEquals(1.0, xpath.evaluate("count(goals/goal)", execution, XPathConstants.NUMBER));
        assertEquals("copy-resources", xpath.evaluate("goals/goal", execution));
        assertEquals("${project.build.outputDirectory}/api-spec", xpath.evaluate("configuration/outputDirectory", execution));
        assertEquals("true", xpath.evaluate("configuration/overwrite", execution));
        assertEquals(1.0, xpath.evaluate("count(configuration/resources/resource)", execution, XPathConstants.NUMBER));
        assertEquals("${project.basedir}/src/main/resources", xpath.evaluate("configuration/resources/resource/directory", execution));
        assertEquals("false", xpath.evaluate("configuration/resources/resource/filtering", execution));
        assertEquals(1.0, xpath.evaluate("count(configuration/resources/resource/includes/include)", execution, XPathConstants.NUMBER));
        assertEquals("openapi.yml", xpath.evaluate("configuration/resources/resource/includes/include", execution));
        assertEquals("${project.basedir}/src/main/resources/openapi.yml", xpath.evaluate("/project/build/plugins/plugin[artifactId='openapi-generator-maven-plugin']/executions/execution/configuration/inputSpec", pom));
    }
    @Test
    void binaryFileBindingsUseTheSupportedWebClientAbstraction() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
        var pom = factory.newDocumentBuilder().parse(module.resolve("../core-common-document-mgmt-sdk/pom.xml").normalize().toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        var configuration = "/project/build/plugins/plugin[artifactId='openapi-generator-maven-plugin']/executions/execution[id='core-common-document-mgmt-sdk']/configuration";
        assertEquals("webclient", xpath.evaluate(configuration + "/library", pom));
        assertEquals(1.0, xpath.evaluate("count(" + configuration + "/configOptions/useAbstractionForFiles)", pom, XPathConstants.NUMBER));
        assertEquals("true", xpath.evaluate(configuration + "/configOptions/useAbstractionForFiles", pom));
    }

}
