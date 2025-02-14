/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */

package org.odpi.openmetadata.adapters.connectors.integration.lineage.sample;


import org.junit.jupiter.api.Test;
import org.odpi.openmetadata.accessservices.assetmanager.metadataelements.*;
import org.odpi.openmetadata.accessservices.assetmanager.properties.SchemaAttributeProperties;
import org.odpi.openmetadata.accessservices.assetmanager.properties.SchemaTypeProperties;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.InvalidParameterException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.PropertyServerException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.UserNotAuthorizedException;
import org.odpi.openmetadata.integrationservices.lineage.connector.LineageIntegratorContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Test of the event parsing into the EventContent object
 */
public class SampleLineageEventProcessorTest {

    private final String topicNamespace = "";

    @Test
    void testEventProcessor() throws IOException, ConnectorCheckedException, InvalidParameterException, PropertyServerException, UserNotAuthorizedException {
        String textPath = "src/test/resources/Sample1.json";
        Path path = Paths.get(textPath);
        String content = Files.readString(path);
        LineageEventContentforSample eventContent = new LineageEventContentforSample(content, "", topicNamespace);
        LineageIntegratorContext mockContext = new MockLineageIntegratorContext();
        SampleLineageEventProcessor eventProcessor = new SampleLineageEventProcessor(mockContext, null, "");
        eventProcessor.processEvent(eventContent);
        List<DataAssetElement> assetList = mockContext.getDataAssetsByName("C6B7B1B717C840F686EE2426241ED18CE1D053019534F03495E8CD644976FA37", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        DataAssetElement inputDataAssetElement = assetList.get(0);
        assertTrue(inputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("Foo"));
        assertTrue(inputDataAssetElement.getElementHeader().getGUID() != null);


        assetList = mockContext.getDataAssetsByName("vertriebskunde-services.agree-kundendaten", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        DataAssetElement outputDataAssetElement = assetList.get(0);
        assertTrue(outputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("vertriebskunde-services.agree-kundendaten"));
        assertTrue(outputDataAssetElement.getElementHeader().getGUID() != null);

        List<ProcessElement> processElementList = mockContext.getProcessesByName("1234567890", 0, 1000, new Date());
        assertTrue(processElementList != null);
        assertTrue(!processElementList.isEmpty());
        ProcessElement processElement = processElementList.get(0);
        assertTrue("1234567890".equals(processElement.getProcessProperties().getQualifiedName()));
        assertTrue("TestRes".equals(processElement.getProcessProperties().getTechnicalName()));

        List<DataFlowElement> dataFlowsIn = ((MockLineageIntegratorContext) mockContext).getDataflows(inputDataAssetElement.getElementHeader().getGUID(), processElement.getElementHeader().getGUID(), new Date());
        assertTrue(dataFlowsIn != null);
        assertTrue(!dataFlowsIn.isEmpty());
        assertTrue(dataFlowsIn.size() == 1);
        DataFlowElement dataFlowElement = dataFlowsIn.get(0);
        assertTrue(dataFlowElement.getDataFlowProperties().getFormula().equals("SQL - select * from foo;"));
        assertTrue(dataFlowElement.getDataFlowHeader().getGUID() != null);
        List<DataFlowElement> dataFlowsOut = ((MockLineageIntegratorContext) mockContext).getDataflows(processElement.getElementHeader().getGUID(), outputDataAssetElement.getElementHeader().getGUID(), new Date());
        assertTrue(dataFlowsOut != null);
        assertTrue(!dataFlowsOut.isEmpty());
        assertTrue(dataFlowsOut.size() == 1);
        dataFlowElement = dataFlowsOut.get(0);
        assertTrue(dataFlowElement.getDataFlowProperties().getFormula() == null);
        assertTrue(dataFlowElement.getDataFlowHeader().getGUID() != null);

        // check that the schematype and schema attributes are created

        SchemaTypeElement schemaTypeElement = mockContext.getSchemaTypeForElement(outputDataAssetElement.getElementHeader().getGUID(), "KafkaTopic", new Date());

        assertTrue(schemaTypeElement != null);
        String schemaTypeElementGUID = schemaTypeElement.getElementHeader().getGUID();
        assertTrue(schemaTypeElementGUID != null);
        SchemaTypeProperties schemaTypeProperties = schemaTypeElement.getSchemaTypeProperties();
        assertTrue(schemaTypeProperties != null);
        assertTrue(schemaTypeProperties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-EventTypes"));
        assertTrue(schemaTypeProperties.getDisplayName().equals("vertriebskunde-services.agree-kundendaten-EventTypes"));
        List<SchemaTypeElement> schemaTypeElementList = schemaTypeElement.getSchemaOptions();
        List<SchemaAttributeElement> attributes = new ArrayList<>();
        for (SchemaTypeElement schemaTypeElement1 : schemaTypeElementList) {
            attributes.addAll(mockContext.getNestedSchemaAttributes(schemaTypeElement1.getElementHeader().getGUID(), 0, 0, new Date()));
        }
        assertTrue(attributes != null);
        assertTrue(attributes.size() == 5);
        boolean firstNameFound = false;
        boolean lastNameFound = false;
        boolean ageFound = false;
        boolean derivedAgeFound = false;
        boolean derivedAge2Found = false;
        for (SchemaAttributeElement attribute : attributes) {
            SchemaAttributeProperties properties = attribute.getSchemaAttributeProperties();
            if (properties.getDisplayName().equals("firstName")) {
                assertTrue(properties.getDescription().equals("The person's first name."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten~Person~firstName"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                firstNameFound = true;
            } else if (properties.getDisplayName().equals("lastName")) {
                assertTrue(properties.getDescription().equals("The person's last name."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten~Person~lastName"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                lastNameFound = true;
            } else if (properties.getDisplayName().equals("age")) {
                assertTrue(properties.getDescription().equals("Age in years which must be equal to or greater than zero."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten~Person~age"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                ageFound = true;
            } else if (properties.getDisplayName().equals("derivedAge")) {
                assertTrue(properties.getDescription().equals("Derived Age."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten~Person~derivedAge"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                derivedAgeFound = true;
            } else if (properties.getDisplayName().equals("derivedAge2")) {
                assertTrue(properties.getDescription().equals("Derived Age."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten~Person~derivedAge2"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                derivedAge2Found = true;
            }
        }
        assertTrue(firstNameFound);
        assertTrue(lastNameFound);
        assertTrue(ageFound);
        assertTrue(derivedAgeFound);
        assertTrue(derivedAge2Found);

        // test update assets and processes

        textPath = "src/test/resources/Sample2-update-assets-and-process.json";
        path = Paths.get(textPath);
        content = Files.readString(path);
        eventContent = new LineageEventContentforSample(content, "", topicNamespace);

        eventProcessor = new SampleLineageEventProcessor(mockContext, null, "");
        eventProcessor.processEvent(eventContent);
        assetList = mockContext.getDataAssetsByName("C6B7B1B717C840F686EE2426241ED18CE1D053019534F03495E8CD644976FA37", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        inputDataAssetElement = assetList.get(0);
        assertTrue(inputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("Foo-2"));
        assertTrue(inputDataAssetElement.getElementHeader().getGUID() != null);


        assetList = mockContext.getDataAssetsByName("vertriebskunde-services.agree-kundendaten", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        outputDataAssetElement = assetList.get(0);
        assertTrue(outputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("vertriebskunde-services.agree-kundendaten"));
        assertTrue(outputDataAssetElement.getElementHeader().getGUID() != null);


        processElementList = mockContext.getProcessesByName("1234567890", 0, 1000, new Date());
        assertTrue(processElementList != null);
        assertTrue(!processElementList.isEmpty());
        processElement = processElementList.get(0);
        assertTrue("1234567890".equals(processElement.getProcessProperties().getQualifiedName()));
        assertTrue("TestRes-2".equals(processElement.getProcessProperties().getTechnicalName()));
        assertTrue("TestResource-2".equals(processElement.getProcessProperties().getTechnicalDescription()));

        // replace assets

        textPath = "src/test/resources/Sample3-replace-assets.json";
        path = Paths.get(textPath);
        content = Files.readString(path);
        eventContent = new LineageEventContentforSample(content, "", topicNamespace);

        eventProcessor = new SampleLineageEventProcessor(mockContext, null, "");
        eventProcessor.processEvent(eventContent);
        assetList = mockContext.getDataAssetsByName("C6B7B1B717C840F686EE2426241ED18CE1D053019534F03495E8CD644976FA37-3", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        inputDataAssetElement = assetList.get(0);
        assertTrue(inputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("Foo-3"));
        assertTrue(inputDataAssetElement.getElementHeader().getGUID() != null);


        assetList = mockContext.getDataAssetsByName("vertriebskunde-services.agree-kundendaten-3", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        outputDataAssetElement = assetList.get(0);
        assertTrue(outputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("vertriebskunde-services.agree-kundendaten-3"));
        assertTrue(outputDataAssetElement.getElementHeader().getGUID() != null);


        processElementList = mockContext.getProcessesByName("1234567890", 0, 1000, new Date());
        assertTrue(processElementList != null);
        assertTrue(!processElementList.isEmpty());
        processElement = processElementList.get(0);
        assertTrue("1234567890".equals(processElement.getProcessProperties().getQualifiedName()));
        assertTrue("TestRes-3".equals(processElement.getProcessProperties().getTechnicalName()));
        assertTrue("TestResource-3".equals(processElement.getProcessProperties().getTechnicalDescription()));

        // add delete and update attributes

        textPath = "src/test/resources/Sample4-update-attributes.json";
        path = Paths.get(textPath);
        content = Files.readString(path);
        eventContent = new LineageEventContentforSample(content, "", topicNamespace);

        eventProcessor = new SampleLineageEventProcessor(mockContext, null, "");
        eventProcessor.processEvent(eventContent);
        assetList = mockContext.getDataAssetsByName("C6B7B1B717C840F686EE2426241ED18CE1D053019534F03495E8CD644976FA37-3", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        inputDataAssetElement = assetList.get(0);
        assertTrue(inputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("Foo-3"));
        assertTrue(inputDataAssetElement.getElementHeader().getGUID() != null);


        assetList = mockContext.getDataAssetsByName("vertriebskunde-services.agree-kundendaten-3", 0, 1000, new Date());
        assertTrue(assetList != null);
        assertTrue(assetList.size() == 1);
        outputDataAssetElement = assetList.get(0);
        assertTrue(outputDataAssetElement.getDataAssetProperties().getTechnicalName().equals("vertriebskunde-services.agree-kundendaten-3"));
        assertTrue(outputDataAssetElement.getElementHeader().getGUID() != null);


        processElementList = mockContext.getProcessesByName("1234567890", 0, 1000, new Date());
        assertTrue(processElementList != null);
        assertTrue(!processElementList.isEmpty());
        processElement = processElementList.get(0);
        assertTrue("1234567890".equals(processElement.getProcessProperties().getQualifiedName()));
        assertTrue("TestRes-3".equals(processElement.getProcessProperties().getTechnicalName()));
        assertTrue("TestResource-3".equals(processElement.getProcessProperties().getTechnicalDescription()));
        schemaTypeElement = mockContext.getSchemaTypeForElement(outputDataAssetElement.getElementHeader().getGUID(), "KafkaTopic", new Date());
        schemaTypeProperties = schemaTypeElement.getSchemaTypeProperties();

        assertTrue(schemaTypeElement != null);
        schemaTypeElementGUID = schemaTypeElement.getElementHeader().getGUID();
        assertTrue(schemaTypeElementGUID != null);
        assertTrue(schemaTypeProperties != null);
        assertTrue(schemaTypeProperties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-3-EventTypes"));
        assertTrue(schemaTypeProperties.getDisplayName().equals("vertriebskunde-services.agree-kundendaten-3-EventTypes"));
        schemaTypeElementList = schemaTypeElement.getSchemaOptions();
        attributes = new ArrayList<>();
        for (SchemaTypeElement schemaTypeElement1 : schemaTypeElementList) {
            attributes.addAll(mockContext.getNestedSchemaAttributes(schemaTypeElement1.getElementHeader().getGUID(), 0, 0, new Date()));
        }

        assertTrue(attributes != null);
        assertTrue(attributes.size() == 5);
        firstNameFound = false;
        boolean middleNameFound = false;
        ageFound = false;
        derivedAgeFound = false;
        derivedAge2Found = false;
        for (SchemaAttributeElement attribute : attributes) {
            SchemaAttributeProperties properties = attribute.getSchemaAttributeProperties();
            if (properties.getDisplayName().equals("firstName")) {
                assertTrue(properties.getDescription().equals("The person's first name or name to be called."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-3~Person~firstName"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                firstNameFound = true;
            } else if (properties.getDisplayName().equals("middleName")) {
                assertTrue(properties.getDescription().equals("The person's middle name."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-3~Person~middleName"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                middleNameFound = true;
            } else if (properties.getDisplayName().equals("derivedAge")) {
                assertTrue(properties.getDescription().equals("Derived Age."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-3~Person~derivedAge"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                derivedAgeFound = true;
            } else if (properties.getDisplayName().equals("derivedAge2")) {
                assertTrue(properties.getDescription().equals("Derived Age 2 without formula."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-3~Person~derivedAge2"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                derivedAge2Found = true;
            } else if (properties.getDisplayName().equals("age")) {
                assertTrue(properties.getDescription().equals("Age as a string to test type change."));
                assertTrue(properties.getQualifiedName().equals("vertriebskunde-services.agree-kundendaten-3~Person~age"));
                assertTrue(properties.getTypeName().equals("EventSchemaAttribute"));
                ageFound = true;
            }
        }
        assertTrue(firstNameFound);
        assertTrue(middleNameFound);
        assertTrue(ageFound);
        assertTrue(derivedAgeFound);
        assertTrue(derivedAge2Found);
    }

    @Test
    void testEventProcessorRealExample() throws IOException, ConnectorCheckedException {
        String textPath = "src/test/resources/Sample-real-anonymous.json";
        Path path = Paths.get(textPath);
        String content = Files.readString(path);
        LineageEventContentforSample eventContent = new LineageEventContentforSample(content, "", topicNamespace);
        LineageIntegratorContext mockContext = new MockLineageIntegratorContext();
        SampleLineageEventProcessor eventProcessor = new SampleLineageEventProcessor(mockContext, null, "");
        eventProcessor.processEvent(eventContent);
    }
}


