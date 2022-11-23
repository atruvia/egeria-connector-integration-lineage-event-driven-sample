/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */

package org.odpi.openmetadata.adapters.connectors.integration.lineage;

import org.odpi.openmetadata.accessservices.assetmanager.metadataelements.*;
import org.odpi.openmetadata.accessservices.assetmanager.properties.*;
import org.odpi.openmetadata.frameworks.connectors.ffdc.InvalidParameterException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.PropertyServerException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.UserNotAuthorizedException;
import org.odpi.openmetadata.integrationservices.lineage.connector.LineageIntegratorContext;
import org.odpi.openmetadata.repositoryservices.connectors.openmetadatatopic.OpenMetadataTopicConnector;

import java.util.*;

/**
 * This class processes an event. The code here has been extracted from the integration connector, so it is easier to unit test.
 */
@SuppressWarnings("JavaUtilDate")
public class EventProcessor  {

    private LineageIntegratorContext                myContext;
    private  List<String> inAssetGUIDs = null;
    private  List<String> outAssetGUIDs = null;



    public EventProcessor(LineageIntegratorContext  myContext ) {
        this.myContext = myContext;
    }

    public void processEvent(EventContent eventContent)
    {
        try {
            // upsert in assets
            inAssetGUIDs = upsertAssets(eventContent.getInputAssets() );
            // upsert out assets
            outAssetGUIDs = upsertAssets(eventContent.getOutputAssets());
            saveLineage(eventContent);

        } catch (InvalidParameterException e) {
            throw new RuntimeException(e);
        } catch (PropertyServerException e) {
            throw new RuntimeException(e);
        } catch (UserNotAuthorizedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The AssetFromJSON represents the asset as specified in the json. Alist of these are supplied to be put into the
     * metadata repository. The method does na upsert, i.e. updates if the asset already exists otherwise inserts.
     *
     * Because we look the asset up by name we can get more than one returned to us. This code assumes the first one is the
     * relevant one,
     *
     * @param jsonAssets json assets
     * @return a list of qualified names of the processed assets
     * @throws InvalidParameterException invalid parameter exception
     * @throws UserNotAuthorizedException user is not authorised
     * @throws PropertyServerException property server Exception
     */
    public List<String> upsertAssets(  List<EventContent.AssetFromJSON> jsonAssets) throws InvalidParameterException, UserNotAuthorizedException, PropertyServerException {
        List<String> assetGUIDs = new ArrayList<>();
        for (EventContent.AssetFromJSON jsonAsset:jsonAssets) {
            String assetQualifiedName = jsonAsset.getQualifiedName();
            String assetGUID = null;
            List<DataAssetElement> dataAssetElements = myContext.getDataAssetsByName(assetQualifiedName, 0, 1000, new Date());
            DataAssetProperties assetProperties = new DataAssetProperties();
            assetProperties.setTypeName(jsonAsset.getTypeName());
            assetProperties.setQualifiedName(assetQualifiedName);
            assetProperties.setDisplayName(jsonAsset.getDisplayName());
            if (dataAssetElements == null || dataAssetElements.isEmpty()) {
                // create asset
                assetGUID = myContext.createDataAsset(true, assetProperties);
            } else {
                // asset already exists - update it
                DataAssetElement  dataAssetElement = dataAssetElements.get(0);
                if ( dataAssetElement.getElementHeader() != null) {
                    assetGUID = dataAssetElement.getElementHeader().getGUID();
                    myContext.updateDataAsset(assetGUID, false, assetProperties, new Date());
                }
            }
            if (assetGUID != null) {
                assetGUIDs.add(assetGUID);
                if (jsonAsset.getEventType() != null) {
                    ensureSchemaIsCatalogued(jsonAsset, assetGUID);
                }
            } else {
                //error
            }

        }
        // remember asset
        return assetGUIDs;
    }

    /**
     * This code ensure that the schema associated with the supplied asset is appropriately catalogued.
     * The asset that is supplied is expected to be a Kafka topic and the schema is mapped
     * to event schema entities.
     *
     * This method does create, update delete on EventType (the schema type) and its schema attributes.
     * Deletion of the Event type is assumed to take out any schema attributes under it.
     * @param assetFromJSON - the asset from the json
     * @param assetGUID - asset GUID
     * @throws InvalidParameterException invalid parameter exception
     * @throws UserNotAuthorizedException user is not authorised
     * @throws PropertyServerException property server Exception
     */

    private void ensureSchemaIsCatalogued(EventContent.AssetFromJSON assetFromJSON, String assetGUID) throws InvalidParameterException, PropertyServerException, UserNotAuthorizedException {
        EventContent.EventTypeFromJSON eventTypeFromJson = assetFromJSON.getEventType();


        SchemaTypeElement childSchemaType = myContext.getSchemaTypeForElement(
                assetGUID,
                assetFromJSON.getTypeName(),
                new Date());

        SchemaTypeProperties schemaTypeProperties = new SchemaTypeProperties();
        schemaTypeProperties.setTypeName("EventType");
        EventContent.EventTypeFromJSON eventTypeFromJSON = assetFromJSON.getEventType();
        schemaTypeProperties.setQualifiedName(eventTypeFromJSON.getQualifiedName());
        schemaTypeProperties.setDisplayName(eventTypeFromJSON.getDisplayName());
        String jsonEventTypeQualifiedName = eventTypeFromJson.getQualifiedName();
        String schemaTypeGUID =null;

        if (childSchemaType ==null) {
            // create schema type as there is no child schema type
            schemaTypeGUID = myContext.createSchemaType( true, schemaTypeProperties);
            //link to asset
            myContext.setupSchemaTypeParent(true,schemaTypeGUID,assetGUID,"KafkaTopic",null, new Date());

            // For each schema attribute create it
            for (EventContent.Attribute attribute:eventTypeFromJSON.getAttributes()) {
                SchemaAttributeProperties schemaAttributeProperties = new SchemaAttributeProperties();
                schemaAttributeProperties.setQualifiedName(attribute.getQualifiedName());
                schemaAttributeProperties.setDisplayName(attribute.getName());
                schemaAttributeProperties.setTypeName(attribute.getType());
                schemaAttributeProperties.setDescription(attribute.getDescription());
                myContext.createSchemaAttribute(true,schemaTypeGUID,schemaAttributeProperties,new Date());
            }
        } else {
            // either the existing schema type is us - so we should update it or it is not and we should delete it
            schemaTypeGUID = childSchemaType.getElementHeader().getGUID();
            if (jsonEventTypeQualifiedName.equals(childSchemaType.getSchemaTypeProperties().getQualifiedName())) {
                // update


                myContext.updateSchemaType(schemaTypeGUID, false, schemaTypeProperties, new Date());

                // check the schema attributes
                List<SchemaAttributeElement> existingSchemaAttributes = myContext.getNestedSchemaAttributes(schemaTypeGUID, 0, 1000, new Date());

                Map<String, SchemaAttributeElement> existingSchemaAttributesMap = new HashMap<>();
                Map<String, EventContent.Attribute> jsonAttributeMap = new HashMap<>();
                for (SchemaAttributeElement schemaAttributeElement : existingSchemaAttributes) {
                    existingSchemaAttributesMap.put(schemaAttributeElement.getSchemaAttributeProperties().getQualifiedName(), schemaAttributeElement);
                }
                for (EventContent.Attribute attribute : eventTypeFromJson.getAttributes()) {
                    jsonAttributeMap.put(attribute.getQualifiedName(), attribute);
                }

                // TODO loops to determine updates and deletes for schema attributes

               // final Set<String> existingKeySet = existingSchemaAttributesMap.keySet();
                final Set<String> jsonKeySet = jsonAttributeMap.keySet();

              //  List<String> updateSchemaAttributeGUIDs = new ArrayList<>();
                Map<String, EventContent.Attribute> updateGUIDToSchemaPropertyAttributesMap = new HashMap<>();
                List<String> deleteSchemaAttributeGUIDs = new ArrayList<>();

                for (Map.Entry<String, SchemaAttributeElement>  entry :existingSchemaAttributesMap.entrySet()) {
                    String existingGUID = entry.getValue().getElementHeader().getGUID();
                    if (jsonKeySet.contains(entry.getKey())) {
                      //  updateSchemaAttributeGUIDs.add(existingGUID);
                        updateGUIDToSchemaPropertyAttributesMap.put(existingGUID,jsonAttributeMap.get(entry.getKey()));
                    }else {
                        deleteSchemaAttributeGUIDs.add(existingGUID);
                    }
                }

                // action the delete attributes
                for (String schemaAttributeGUID : deleteSchemaAttributeGUIDs) {
                    myContext.removeSchemaAttribute(schemaAttributeGUID, new Date());
                }
                // action updates
                Set<String> updatedQNames = new HashSet<>();
                for (Map.Entry<String, EventContent.Attribute> entry : updateGUIDToSchemaPropertyAttributesMap.entrySet()) {
                    EventContent.Attribute jsonAttribute =entry.getValue();
                    SchemaAttributeProperties schemaAttributeProperties = new SchemaAttributeProperties();
                    schemaAttributeProperties.setQualifiedName(jsonAttribute.getQualifiedName());
                    updatedQNames.add(jsonAttribute.getQualifiedName());
                    schemaAttributeProperties.setDisplayName(jsonAttribute.getName());
                    schemaAttributeProperties.setTypeName(jsonAttribute.getType());
                    schemaAttributeProperties.setDescription(jsonAttribute.getDescription());
                    myContext.updateSchemaAttribute(entry.getKey(), false,schemaAttributeProperties , new Date());
                }

                // action adds. Add only those attributes that do not already exist
                // For each schema attribute create it
                for (EventContent.Attribute attribute:eventTypeFromJSON.getAttributes()) {
                    if (!updatedQNames.contains(attribute.getQualifiedName())) {
                        SchemaAttributeProperties schemaAttributeProperties = new SchemaAttributeProperties();
                        schemaAttributeProperties.setQualifiedName(attribute.getQualifiedName());
                        schemaAttributeProperties.setDisplayName(attribute.getName());
                        schemaAttributeProperties.setTypeName(attribute.getType());
                        schemaAttributeProperties.setDescription(attribute.getDescription());
                        myContext.createSchemaAttribute(true, schemaTypeGUID, schemaAttributeProperties, new Date());
                    }
                }

            } else {
                // delete - this should cascade and delete any children.
                myContext.removeSchemaType(schemaTypeGUID, new Date());
                // add the new one
                schemaTypeGUID = myContext.createSchemaType( true, schemaTypeProperties);
                //link to asset
                myContext.setupSchemaTypeParent(true,schemaTypeGUID,assetGUID,"KafkaTopic",null, new Date());
                // For each schema attribute create it
                for (EventContent.Attribute attribute:eventTypeFromJSON.getAttributes()) {
                    SchemaAttributeProperties schemaAttributeProperties = new SchemaAttributeProperties();
                    schemaAttributeProperties.setQualifiedName(attribute.getQualifiedName());
                    schemaAttributeProperties.setDisplayName(attribute.getName());
                    schemaAttributeProperties.setTypeName(attribute.getType());
                    schemaAttributeProperties.setDescription(attribute.getDescription());
                    myContext.createSchemaAttribute(true,schemaTypeGUID,schemaAttributeProperties,new Date());
                }
            }
        }
        // the schema should now be there reflecting the event values.


    }

    /**
     * Save the lineage. The input and assets will have been catalogued prior to this method.
     *
     * This method creates a process entity then knits it to the input and output assets.
     *
     * This is creating asset level lineage not column level.
     *
     * The relationship between the input asset and the process is a DataFlow relationship which contains
     * the formula, which is the SQL.
     *
     * @param eventContent - representation of the event as a java object.
     * @throws InvalidParameterException invalid parameter exception
     * @throws UserNotAuthorizedException user is not authorised
     * @throws PropertyServerException property server Exception
     */
    private void saveLineage(EventContent eventContent) throws InvalidParameterException, PropertyServerException, UserNotAuthorizedException {
        String processQualifiedName = eventContent.getProcessQualifiedName();
        String processGUID = null;


        List<ProcessElement> processElementList = myContext.getProcessesByName(processQualifiedName,0, 1000, new Date());

        ProcessProperties processProperties = new ProcessProperties();
        processProperties.setQualifiedName(processQualifiedName);
        processProperties.setDisplayName(eventContent.getProcessDisplayName());
        processProperties.setDescription(eventContent.getProcessDescription());
        // does this process already exist?
        if(processElementList == null || processElementList.isEmpty()) {
            // process does not exist
            processGUID = myContext.createProcess(true, ProcessStatus.ACTIVE, processProperties);
        } else {
            // process exists update it
            ProcessElement processElement = processElementList.get(0);
            processGUID = processElement.getElementHeader().getGUID();
            myContext.updateProcess(processGUID,false, processProperties, new Date());


        }
        // now grab all the calling and called relationships.
//        List<ProcessCallElement> processCallElements = myContext.getProcessCallers(processGUID, 0, 1000, new Date());
//        List<ProcessCallElement> processCalledElements = myContext.getProcessCalled(processGUID,0, 1000, new Date());

        for (String assetGUID : inAssetGUIDs) {
            DataFlowProperties properties = new DataFlowProperties();
            DataAssetElement dataAssetElement = myContext.getDataAssetByGUID(assetGUID, new Date());

            String sql = eventContent.getSQLForInputAsset(dataAssetElement.getDataAssetProperties().getQualifiedName());
            if (sql != null) {
                properties.setFormula(sql);
            }
            // if there is already a dataflow - update it, if not create it
            DataFlowElement  existingDataflow = myContext.getDataFlow(assetGUID, processGUID, null, new Date());
            if (existingDataflow == null) {
                myContext.setupDataFlow(true, assetGUID, processGUID, properties, new Date());

            } else {
                myContext.updateDataFlow(existingDataflow.getDataFlowHeader().getGUID(), properties, new Date());
            }
        }
        for (String assetGUID : outAssetGUIDs) {
            DataFlowProperties properties = new DataFlowProperties();
            DataFlowElement  existingDataflow = myContext.getDataFlow( processGUID,assetGUID, null, new Date());
            if (existingDataflow == null) {
                myContext.setupDataFlow(true, processGUID, assetGUID, properties, new Date());
            } else {
                myContext.updateDataFlow(existingDataflow.getDataFlowHeader().getGUID(), properties, new Date());
            }
        }

    }
}
