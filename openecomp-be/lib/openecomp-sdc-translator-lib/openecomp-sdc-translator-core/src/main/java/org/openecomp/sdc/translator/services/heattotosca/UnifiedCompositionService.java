/*
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.sdc.translator.services.heattotosca;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.openecomp.config.api.Configuration;
import org.openecomp.config.api.ConfigurationManager;
import org.openecomp.core.utilities.CommonMethods;
import org.openecomp.core.utilities.yaml.YamlUtil;
import org.openecomp.sdc.datatypes.configuration.ImplementationConfiguration;
import org.openecomp.sdc.heat.services.HeatConstants;
import org.openecomp.sdc.logging.api.Logger;
import org.openecomp.sdc.logging.api.LoggerFactory;
import org.openecomp.sdc.logging.context.impl.MdcDataDebugMessage;
import org.openecomp.sdc.tosca.datatypes.ToscaFunctions;
import org.openecomp.sdc.tosca.datatypes.ToscaGroupType;
import org.openecomp.sdc.tosca.datatypes.ToscaNodeType;
import org.openecomp.sdc.tosca.datatypes.ToscaRelationshipType;
import org.openecomp.sdc.tosca.datatypes.model.CapabilityDefinition;
import org.openecomp.sdc.tosca.datatypes.model.Constraint;
import org.openecomp.sdc.tosca.datatypes.model.EntrySchema;
import org.openecomp.sdc.tosca.datatypes.model.GroupDefinition;
import org.openecomp.sdc.tosca.datatypes.model.NodeTemplate;
import org.openecomp.sdc.tosca.datatypes.model.NodeType;
import org.openecomp.sdc.tosca.datatypes.model.ParameterDefinition;
import org.openecomp.sdc.tosca.datatypes.model.PropertyType;
import org.openecomp.sdc.tosca.datatypes.model.RelationshipTemplate;
import org.openecomp.sdc.tosca.datatypes.model.RequirementAssignment;
import org.openecomp.sdc.tosca.datatypes.model.ServiceTemplate;
import org.openecomp.sdc.tosca.datatypes.model.SubstitutionMapping;
import org.openecomp.sdc.tosca.datatypes.model.heatextend.PropertyTypeExt;
import org.openecomp.sdc.tosca.services.DataModelUtil;
import org.openecomp.sdc.tosca.services.ToscaAnalyzerService;
import org.openecomp.sdc.tosca.services.ToscaConstants;
import org.openecomp.sdc.tosca.services.ToscaUtil;
import org.openecomp.sdc.tosca.services.impl.ToscaAnalyzerServiceImpl;
import org.openecomp.sdc.translator.datatypes.heattotosca.TranslationContext;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.composition.UnifiedCompositionData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.composition.UnifiedCompositionEntity;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.composition.UnifiedCompositionMode;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.composition.UnifiedSubstitutionData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.ComputeTemplateConsolidationData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.ConsolidationData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.EntityConsolidationData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.FileComputeConsolidationData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.GetAttrFuncData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.NestedTemplateConsolidationData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.PortTemplateConsolidationData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.RequirementAssignmentData;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.consolidation.TypeComputeConsolidationData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.openecomp.sdc.translator.services.heattotosca.Constants.ABSTRACT_NODE_TEMPLATE_ID_PREFIX;
import static org.openecomp.sdc.translator.services.heattotosca.Constants.IDENTICAL_VALUE_PROPERTY_PREFIX;
import static org.openecomp.sdc.translator.services.heattotosca.Constants.IDENTICAL_VALUE_PROPERTY_SUFFIX;

public class UnifiedCompositionService {

  protected static Logger logger =
      (Logger) LoggerFactory.getLogger(UnifiedCompositionService.class);
  protected static MdcDataDebugMessage mdcDataDebugMessage = new MdcDataDebugMessage();
  private static Map<String, ImplementationConfiguration> unifiedCompositionImplMap;

  static {
    Configuration config = ConfigurationManager.lookup();
    unifiedCompositionImplMap =
        config.populateMap(ConfigConstants.MANDATORY_UNIFIED_MODEL_NAMESPACE,
            ConfigConstants.UNIFIED_COMPOSITION_IMPL_KEY, ImplementationConfiguration.class);

  }

  private ConsolidationService consolidationService = new ConsolidationService();

  private static List<EntityConsolidationData> getPortConsolidationDataList(
      Set<String> portIds,
      List<UnifiedCompositionData> unifiedCompositionDataList) {
    List<EntityConsolidationData> portConsolidationDataList = new ArrayList<>();
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      for (PortTemplateConsolidationData portTemplateConsolidationData : unifiedCompositionData
          .getPortTemplateConsolidationDataList()) {
        if (portIds.contains(portTemplateConsolidationData.getNodeTemplateId())) {
          portConsolidationDataList.add(portTemplateConsolidationData);
        }
      }
    }
    return portConsolidationDataList;
  }

  /**
   * Create unified composition.
   *
   * @param serviceTemplate            the service template
   * @param nestedServiceTemplate      the nested service template
   * @param unifiedCompositionDataList the unified composition data list. In case no consolidation,
   *                                   one entry will be in this list, in case of having
   *                                   consolidation, all entries in the list are the once which
   *                                   need to be consolidated.
   * @param mode                       the mode
   * @param context                    the context
   */
  public void createUnifiedComposition(ServiceTemplate serviceTemplate,
                                       ServiceTemplate nestedServiceTemplate,
                                       List<UnifiedCompositionData> unifiedCompositionDataList,
                                       UnifiedCompositionMode mode, TranslationContext context) {
    mdcDataDebugMessage.debugEntryMessage(null, null);
    Optional<UnifiedComposition> unifiedCompositionInstance = getUnifiedCompositionInstance(mode);
    if (!unifiedCompositionInstance.isPresent()) {
      return;
    }
    unifiedCompositionInstance.get()
        .createUnifiedComposition(serviceTemplate, nestedServiceTemplate,
            unifiedCompositionDataList, context);
    mdcDataDebugMessage.debugExitMessage(null, null);
  }

  /**
   * Create unified substitution service template according to the input service template, based on
   * the unified composition data.
   *
   * @param serviceTemplate            the service template
   * @param unifiedCompositionDataList the unified composition data list. In case no consolidation,
   *                                   one entry will be in this list, in case of having
   *                                   consolidation, all entries in the list are the once which
   *                                   need to be consolidated.
   * @param context                    the translation context
   * @return the substitution service template
   */
  public Optional<ServiceTemplate> createUnifiedSubstitutionServiceTemplate(
      ServiceTemplate serviceTemplate, List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context, Integer index) {
    if (CollectionUtils.isEmpty(unifiedCompositionDataList)) {
      return Optional.empty();
    }
    UnifiedCompositionData unifiedCompositionData = unifiedCompositionDataList.get(0);
    String templateName =
        getTemplateName(serviceTemplate, unifiedCompositionData, index);
    ServiceTemplate substitutionServiceTemplate =
        HeatToToscaUtil.createInitSubstitutionServiceTemplate(templateName);

    createIndexInputParameter(substitutionServiceTemplate);

    String computeNodeType =
        handleCompute(serviceTemplate, substitutionServiceTemplate, unifiedCompositionDataList,
            context);
    handlePorts(serviceTemplate, substitutionServiceTemplate, unifiedCompositionDataList,
        computeNodeType, context);
    createOutputParameters(serviceTemplate, substitutionServiceTemplate, unifiedCompositionDataList,
        computeNodeType);
    NodeType substitutionGlobalNodeType =
        handleSubstitutionGlobalNodeType(serviceTemplate, substitutionServiceTemplate,
            context, unifiedCompositionData, index);

    HeatToToscaUtil.handleSubstitutionMapping(context,
        getSubstitutionNodeTypeId(serviceTemplate, unifiedCompositionData, index),
        substitutionServiceTemplate, substitutionGlobalNodeType);

    context.getTranslatedServiceTemplates().put(templateName, substitutionServiceTemplate);
    return Optional.of(substitutionServiceTemplate);
  }

  /**
   * Create abstract substitute node template that can be substituted by the input
   * substitutionServiceTemplate.
   *
   * @param serviceTemplate             the service template
   * @param substitutionServiceTemplate the subtitution service template
   * @param unifiedCompositionDataList  the unified composition data list. In case no consolidation,
   *                                    one entry will be in this list, in case of having
   *                                    consolidation, all entries in the list are the once which
   *                                    need to be consolidated.
   * @param context                     the translation context
   * @return the abstract substitute node template id
   */
  public String createAbstractSubstituteNodeTemplate(
      ServiceTemplate serviceTemplate,
      ServiceTemplate substitutionServiceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context,
      Integer index) {

    NodeTemplate substitutionNodeTemplate = new NodeTemplate();
    List<String> directiveList = new ArrayList<>();
    directiveList.add(ToscaConstants.NODE_TEMPLATE_DIRECTIVE_SUBSTITUTABLE);
    substitutionNodeTemplate.setDirectives(directiveList);
    String substituteNodeTemplateType =
        getSubstitutionNodeTypeId(serviceTemplate, unifiedCompositionDataList.get(0), index);
    substitutionNodeTemplate.setType(substituteNodeTemplateType);
    Optional<Map<String, Object>> abstractSubstitutionProperties =
        createAbstractSubstitutionProperties(serviceTemplate,
            substitutionServiceTemplate, unifiedCompositionDataList);
    abstractSubstitutionProperties.ifPresent(substitutionNodeTemplate::setProperties);

    //Add substitution filtering property
    String substitutionServiceTemplateName = ToscaUtil.getServiceTemplateFileName(
        substitutionServiceTemplate);
    int count = unifiedCompositionDataList.size();
    addSubstitutionFilteringProperty(substitutionServiceTemplateName, substitutionNodeTemplate,
        count);
    //Add index_value property
    addIndexValueProperty(substitutionNodeTemplate);
    String substituteNodeTemplateId =
        getSubstituteNodeTemplateId(serviceTemplate, unifiedCompositionDataList.get(0), index);
    //Add node template id and related abstract node template id in context
    addUnifiedSubstitionData(context, serviceTemplate, unifiedCompositionDataList,
        substituteNodeTemplateId);
    DataModelUtil
        .addNodeTemplate(serviceTemplate, substituteNodeTemplateId, substitutionNodeTemplate);
    return substituteNodeTemplateId;

  }

  /**
   * Update the connectivity from/to the "moved" nodes from the original service template to the new
   * substitution service template.
   *
   * @param serviceTemplate            the service template
   * @param unifiedCompositionDataList the unified composition data list. In case no consolidation,
   *                                   one entry will be in this list, in case of having
   *                                   consolidation, all entries in the list are the once which
   *                                   need to be consolidated.
   * @param context                    the translation context
   */
  public void updateCompositionConnectivity(ServiceTemplate serviceTemplate,
                                            List<UnifiedCompositionData> unifiedCompositionDataList,
                                            TranslationContext context) {
    updOutputParamGetAttrInConnectivity(serviceTemplate, unifiedCompositionDataList, context);
    updNodesGetAttrInConnectivity(serviceTemplate, unifiedCompositionDataList, context);
    updNodesConnectedOutConnectivity(serviceTemplate, unifiedCompositionDataList, context);
    updNodesConnectedInConnectivity(serviceTemplate, unifiedCompositionDataList, context);
    updVolumeConnectivity(serviceTemplate, unifiedCompositionDataList, context);
    updGroupsConnectivity(serviceTemplate, unifiedCompositionDataList, context);
  }

  /**
   * Delete the "moved" nodes from the original service template to the new substitution service
   * template.
   *
   * @param serviceTemplate            the service template
   * @param unifiedCompositionDataList the unified composition data list. In case no consolidation,
   *                                   one entry will be in this list, in case of having
   *                                   consolidation, all entries in the list are the once which
   *                                   need to be consolidated.
   * @param context                    the translation context
   */
  public void cleanUnifiedCompositionEntities(
      ServiceTemplate serviceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData =
          unifiedCompositionData.getComputeTemplateConsolidationData();
      cleanServiceTemplate(serviceTemplate, computeTemplateConsolidationData, context);

      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        cleanServiceTemplate(serviceTemplate, portTemplateConsolidationData, context);
      }
    }

  }

  public void cleanNodeTypes(ServiceTemplate serviceTemplate,
                             List<UnifiedCompositionData> unifiedCompositionDataList,
                             TranslationContext context) {
    for (UnifiedCompositionData unifiedData : unifiedCompositionDataList) {
      removeCleanedNodeType(
          unifiedData.getComputeTemplateConsolidationData().getNodeTemplateId(), serviceTemplate,
          context);
    }

    if (MapUtils.isEmpty(serviceTemplate.getNode_types())) {
      serviceTemplate.setNode_types(null);
    }
  }

  /**
   * Update unified abstract nodes connectivity.
   *
   * @param serviceTemplate the service template
   * @param context         the context
   */
  public void updateUnifiedAbstractNodesConnectivity(ServiceTemplate serviceTemplate,
                                                     TranslationContext context) {


    String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
    UnifiedSubstitutionData unifiedSubstitutionData = context.getUnifiedSubstitutionData()
        .get(serviceTemplateFileName);

    if (Objects.nonNull(unifiedSubstitutionData)) {
      //Handle get attribute in connectivity for abstarct node to abstract node templates
      Set<String> abstractNodeIds =
          new HashSet<>(unifiedSubstitutionData.getAllRelatedAbstractNodeIds());
      handleGetAttrInConnectivity(serviceTemplate, abstractNodeIds, context);
      //Handle get attribute in connectivity for abstract node templates to nested node template
      Set<String> nestedNodeIds =
          new HashSet<>(unifiedSubstitutionData.getAllUnifiedNestedNodeTemplateIds());
      handleGetAttrInConnectivity(serviceTemplate, nestedNodeIds, context);
    }
  }

  public void handleUnifiedNestedDefinition(ServiceTemplate mainServiceTemplate,
                                            ServiceTemplate nestedServiceTemplate,
                                            List<UnifiedCompositionData> unifiedCompositionDataList,
                                            TranslationContext context) {
    handleUnifiedNestedNodeType(mainServiceTemplate, nestedServiceTemplate, context);
    updateUnifiedNestedTemplates(mainServiceTemplate, nestedServiceTemplate, unifiedCompositionDataList, context);
  }

  private void handleGetAttrInConnectivity(ServiceTemplate serviceTemplate,
                                                   Set<String> unifiedNodeIds,
                                                   TranslationContext context) {
    Map<String, NodeTemplate> nodeTemplates =
        serviceTemplate.getTopology_template().getNode_templates();
    String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
    for (String unifiedNodeId : unifiedNodeIds) {
      NodeTemplate nodeTemplate = nodeTemplates.get(unifiedNodeId);
      handleGetAttrInAbstractNodeTemplate(serviceTemplate, context, serviceTemplateFileName,
          nodeTemplate);
    }
  }

  private void handleUnifiedNestedNodeType(ServiceTemplate mainServiceTemplate,
                                           ServiceTemplate nestedServiceTemplate,
                                           TranslationContext context) {


    SubstitutionMapping substitutionMappings =
        nestedServiceTemplate.getTopology_template().getSubstitution_mappings();
    String nodeTypeId = substitutionMappings.getNode_type();

    Optional<String> newNestedNodeTypeId =
        getNewNestedNodeTypeId(mainServiceTemplate, nestedServiceTemplate, context);

    if (isNestedServiceTemplateWasHandled(mainServiceTemplate, nestedServiceTemplate, context,
        newNestedNodeTypeId)) {
      context.updateHandledComputeType(
          ToscaUtil.getServiceTemplateFileName(mainServiceTemplate),
          ToscaUtil.getServiceTemplateFileName(nestedServiceTemplate),
          newNestedNodeTypeId.get());
      return;
    }

    ServiceTemplate globalSubstitutionServiceTemplate =
        context.getGlobalSubstitutionServiceTemplate();

    newNestedNodeTypeId.ifPresent(
        newNestedNodeTypeIdVal -> updateNestedNodeType(nodeTypeId, newNestedNodeTypeIdVal,
            nestedServiceTemplate, mainServiceTemplate, globalSubstitutionServiceTemplate,
            context));

  }

  private boolean isNestedServiceTemplateWasHandled(ServiceTemplate mainServiceTemplate,
                                                    ServiceTemplate nestedServiceTemplate,
                                                    TranslationContext context,
                                                    Optional<String> newNestedNodeTypeId) {
    return newNestedNodeTypeId.isPresent()
        && context.isNestedServiceTemplateWasHandled(
        ToscaUtil.getServiceTemplateFileName(mainServiceTemplate),
        ToscaUtil.getServiceTemplateFileName(nestedServiceTemplate));
  }

  private void updateNestedNodeType(String nodeTypeId, String newNestedNodeTypeId,
                                    ServiceTemplate nestedServiceTemplate,
                                    ServiceTemplate mainServiceTemplate,
                                    ServiceTemplate globalSubstitutionServiceTemplate,
                                    TranslationContext context) {
    context.addNestedFileToUsedNestedComputeType(
       ToscaUtil.getServiceTemplateFileName(mainServiceTemplate),
       ToscaUtil.getServiceTemplateFileName(nestedServiceTemplate),
       newNestedNodeTypeId);
    String indexedNodeType =
        handleNestedNodeTypeInGlobalSubstitutionTemplate(nodeTypeId, newNestedNodeTypeId,
            mainServiceTemplate, globalSubstitutionServiceTemplate, context);

    handleSubstitutionMappingInNestedServiceTemplate(indexedNodeType, nestedServiceTemplate);

    context
        .updateHandledComputeType(
            ToscaUtil.getServiceTemplateFileName(mainServiceTemplate),
            ToscaUtil.getServiceTemplateFileName(nestedServiceTemplate),
            newNestedNodeTypeId);
  }

  private String handleNestedNodeTypeInGlobalSubstitutionTemplate(String nodeTypeId,
                                                                  String newNestedNodeTypeId,
                                                                  ServiceTemplate mainServiceTemplate,
                                                                  ServiceTemplate globalSubstitutionServiceTemplate,
                                                                  TranslationContext context) {
    String indexedNodeType =
        getIndexedGlobalNodeTypeId(newNestedNodeTypeId, mainServiceTemplate, context);
    handleNestedNodeTypesInGlobalSubstituteServiceTemplate(nodeTypeId, indexedNodeType,
        globalSubstitutionServiceTemplate, context);
    return indexedNodeType;
  }

  private String getIndexedGlobalNodeTypeId(String newNestedNodeTypeId,
                                            ServiceTemplate mainServiceTemplate,
                                            TranslationContext context) {
    int globalNodeTypeIndex =
        context.getGlobalNodeTypeIndex(ToscaUtil.getServiceTemplateFileName(mainServiceTemplate),
            newNestedNodeTypeId);
    return globalNodeTypeIndex > 0 ?
        newNestedNodeTypeId + "_" + String.valueOf(globalNodeTypeIndex) : newNestedNodeTypeId;
  }

  private void updateUnifiedNestedTemplates(ServiceTemplate mainServiceTemplate,
                                            ServiceTemplate nestedServiceTemplate,
                                            List<UnifiedCompositionData> unifiedCompositionDataList,
                                            TranslationContext context) {

    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      NestedTemplateConsolidationData nestedTemplateConsolidationData =
          unifiedCompositionData.getNestedTemplateConsolidationData();
      if (Objects.isNull(nestedTemplateConsolidationData)) {
        continue;
      }
      handleNestedNodeTemplateInMainServiceTemplate(nestedTemplateConsolidationData.getNodeTemplateId(), mainServiceTemplate, nestedServiceTemplate, context);
    }
  }

  /**
   * Update connectivity for unified nested patterns.
   *
   * @param serviceTemplate            the service template
   * @param nestedServiceTemplate      the nested service template
   * @param unifiedCompositionDataList the unified composition data list
   * @param context                    the context
   */
  public void updateUnifiedNestedConnectivity(ServiceTemplate serviceTemplate,
                                              ServiceTemplate nestedServiceTemplate,
                                              List<UnifiedCompositionData>
                                                  unifiedCompositionDataList,
                                              TranslationContext context) {

    updNestedCompositionNodesConnectedInConnectivity(serviceTemplate, unifiedCompositionDataList,
        context);
    updNestedCompositionNodesGetAttrInConnectivity(serviceTemplate, unifiedCompositionDataList,
        context);
    updNestedCompositionOutputParamGetAttrInConnectivity(serviceTemplate,
        unifiedCompositionDataList, context);
  }


  /**
   * Clean unified nested entities. Update the heat stack group with the new node template ids.
   *
   * @param serviceTemplate            the service template
   * @param unifiedCompositionDataList the unified composition data list
   * @param context                    the context
   */
  public void cleanUnifiedNestedEntities(ServiceTemplate serviceTemplate,
                                         List<UnifiedCompositionData> unifiedCompositionDataList,
                                         TranslationContext context) {
    EntityConsolidationData entityConsolidationData = unifiedCompositionDataList.get(0)
        .getNestedTemplateConsolidationData();
    updateHeatStackGroupNestedComposition(serviceTemplate, entityConsolidationData, context);

  }

  protected void updNodesConnectedOutConnectivity(ServiceTemplate serviceTemplate,
                                                  List<UnifiedCompositionData>
                                                      unifiedCompositionDataList,
                                                  TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData = unifiedCompositionData
          .getComputeTemplateConsolidationData();
      //Add requirements in the abstract node template for nodes connected out for computes
      String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
          computeTemplateConsolidationData.getNodeTemplateId());
      Map<String, List<RequirementAssignmentData>> computeNodesConnectedOut =
          computeTemplateConsolidationData.getNodesConnectedOut();
      if (computeNodesConnectedOut != null) {
        updateRequirementInAbstractNodeTemplate(serviceTemplate, computeTemplateConsolidationData,
            newComputeNodeTemplateId, computeNodesConnectedOut, context);
      }
      String computeType = getComputeTypeSuffix(serviceTemplate, computeTemplateConsolidationData
          .getNodeTemplateId());
      //Add requirements in the abstract node template for nodes connected out for ports
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        String newPortNodeTemplateId = getNewPortNodeTemplateId(portTemplateConsolidationData
            .getNodeTemplateId(), computeType, computeTemplateConsolidationData);
        Map<String, List<RequirementAssignmentData>> portNodesConnectedOut =
            portTemplateConsolidationData.getNodesConnectedOut();
        if (portNodesConnectedOut != null) {
          updateRequirementInAbstractNodeTemplate(serviceTemplate, portTemplateConsolidationData,
              newPortNodeTemplateId, portNodesConnectedOut, context);
        }
      }
    }
  }

  protected void updNodesConnectedInConnectivity(ServiceTemplate serviceTemplate,
                                                 List<UnifiedCompositionData>
                                                     unifiedCompositionDataList,
                                                 TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData = unifiedCompositionData
          .getComputeTemplateConsolidationData();
      //Update requirements in the node template which pointing to the computes
      String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
          computeTemplateConsolidationData.getNodeTemplateId());
      updNodesConnectedInConnectivity(serviceTemplate, computeTemplateConsolidationData,
          newComputeNodeTemplateId, context, false);

      String computeType = getComputeTypeSuffix(serviceTemplate, computeTemplateConsolidationData
          .getNodeTemplateId());
      //Update requirements in the node template which pointing to the ports
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        String newPortNodeTemplateId = getNewPortNodeTemplateId(portTemplateConsolidationData
            .getNodeTemplateId(), computeType, computeTemplateConsolidationData);
        updNodesConnectedInConnectivity(serviceTemplate, portTemplateConsolidationData,
            newPortNodeTemplateId, context, false);
      }
    }
  }

  private void updNodesConnectedInConnectivity(ServiceTemplate serviceTemplate,
                                               EntityConsolidationData entityConsolidationData,
                                               String newNodeTemplateId,
                                               TranslationContext context,
                                               boolean isNested) {
    Map<String, List<RequirementAssignmentData>> nodesConnectedIn =
        entityConsolidationData.getNodesConnectedIn();
    if (nodesConnectedIn == null) {
      //No nodes connected in info
      return;
    }
    for (Map.Entry<String, List<RequirementAssignmentData>> entry : nodesConnectedIn
        .entrySet()) {
      List<RequirementAssignmentData> requirementAssignmentDataList = entry.getValue();
      for (RequirementAssignmentData requirementAssignmentData : requirementAssignmentDataList) {
        RequirementAssignment requirementAssignment = requirementAssignmentData
            .getRequirementAssignment();
        if (!requirementAssignment.getNode().equals(entityConsolidationData
            .getNodeTemplateId())) {
          //The requirement assignment target node should be the one which we are handling in the
          //consolidation object
          continue;
        }
        //Update the requirement assignment object in the original node template
        if (isNested) {
          updateRequirementForNestedCompositionNodesConnectedIn(serviceTemplate,
              requirementAssignmentData, entityConsolidationData, newNodeTemplateId, context);
        } else {
          updateRequirementForNodesConnectedIn(serviceTemplate, requirementAssignmentData,
              entityConsolidationData, entry.getKey(), newNodeTemplateId, context);
        }

      }
    }
  }

  protected void updNestedCompositionNodesConnectedInConnectivity(ServiceTemplate serviceTemplate,
                                                                  List<UnifiedCompositionData>
                                                                      unifiedCompositionDataList,
                                                                  TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      NestedTemplateConsolidationData nestedTemplateConsolidationData = unifiedCompositionData
          .getNestedTemplateConsolidationData();
      //Update requirements in the node template which pointing to the nested nodes
      String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
      Optional<String> newNestedNodeTemplateId = context.getUnifiedNestedNodeTemplateId(
          serviceTemplateFileName, nestedTemplateConsolidationData.getNodeTemplateId());
      newNestedNodeTemplateId.ifPresent(
          newNestedNodeTemplateIdVal -> updNodesConnectedInConnectivity(serviceTemplate,
              nestedTemplateConsolidationData,
              newNestedNodeTemplateIdVal, context, true));
    }
  }

  protected void updVolumeConnectivity(ServiceTemplate serviceTemplate,
                                       List<UnifiedCompositionData>
                                           unifiedCompositionDataList,
                                       TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData = unifiedCompositionData
          .getComputeTemplateConsolidationData();
      //Add requirements in the abstract node template for compute volumes
      String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
          computeTemplateConsolidationData.getNodeTemplateId());
      Map<String, List<RequirementAssignmentData>> computeVolumes =
          computeTemplateConsolidationData.getVolumes();
      if (computeVolumes != null) {
        updateRequirementInAbstractNodeTemplate(serviceTemplate, computeTemplateConsolidationData,
            newComputeNodeTemplateId, computeVolumes, context);
      }
    }
  }

  protected void updGroupsConnectivity(ServiceTemplate serviceTemplate,
                                       List<UnifiedCompositionData>
                                           unifiedCompositionDataList,
                                       TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData = unifiedCompositionData
          .getComputeTemplateConsolidationData();
      //Add requirements in the abstract node template for nodes connected in for computes
      String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
          computeTemplateConsolidationData.getNodeTemplateId());
      updGroupsConnectivity(serviceTemplate, computeTemplateConsolidationData, context);

      String computeType = getComputeTypeSuffix(serviceTemplate, computeTemplateConsolidationData
          .getNodeTemplateId());
      //Add requirements in the abstract node template for nodes connected in for ports
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        String newPortNodeTemplateId = getNewPortNodeTemplateId(portTemplateConsolidationData
            .getNodeTemplateId(), computeType, computeTemplateConsolidationData);
        updGroupsConnectivity(serviceTemplate, portTemplateConsolidationData, context);
      }
    }
  }

  private void updGroupsConnectivity(ServiceTemplate serviceTemplate, EntityConsolidationData
      entityConsolidationData, TranslationContext context) {
    List<String> groupIds = entityConsolidationData.getGroupIds();
    if (groupIds == null) {
      return;
    }
    String oldNodeTemplateId = entityConsolidationData.getNodeTemplateId();
    String abstractNodeTemplateId = context.getUnifiedAbstractNodeTemplateId(
        serviceTemplate, entityConsolidationData.getNodeTemplateId());
    Map<String, GroupDefinition> groups = serviceTemplate.getTopology_template().getGroups();
    if (groups != null) {
      for (String groupId : groupIds) {
        GroupDefinition groupDefinition = groups.get(groupId);
        if (groupDefinition != null) {
          List<String> groupMembers = groupDefinition.getMembers();
          if (groupMembers.contains(oldNodeTemplateId)) {
            //Replace the old node template id
            groupMembers.remove(oldNodeTemplateId);
            if (!groupMembers.contains(abstractNodeTemplateId)) {
              //Add the abstract node template id if not already present
              groupMembers.add(abstractNodeTemplateId);
            }
          }
        }
      }
    }
  }

  protected void updOutputParamGetAttrInConnectivity(
      ServiceTemplate serviceTemplate, List<UnifiedCompositionData> unifiedComposotionDataList,
      TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedComposotionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData =
          unifiedCompositionData.getComputeTemplateConsolidationData();
      String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
          computeTemplateConsolidationData.getNodeTemplateId());

      updOutputParamGetAttrInConnectivity(serviceTemplate, computeTemplateConsolidationData,
          computeTemplateConsolidationData.getNodeTemplateId(), newComputeNodeTemplateId,
          context, false);

      String computeType =
          getComputeTypeSuffix(serviceTemplate,
              computeTemplateConsolidationData.getNodeTemplateId());
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        String newPortNodeTemplateId =
            getNewPortNodeTemplateId(portTemplateConsolidationData.getNodeTemplateId(), computeType,
                computeTemplateConsolidationData);

        updOutputParamGetAttrInConnectivity(serviceTemplate, portTemplateConsolidationData,
            portTemplateConsolidationData.getNodeTemplateId(), newPortNodeTemplateId, context,
            false);
      }
    }
  }

  protected void updNodesGetAttrInConnectivity(
      ServiceTemplate serviceTemplate,
      List<UnifiedCompositionData> unifiedComposotionDataList,
      TranslationContext context) {
    Map<String, UnifiedCompositionEntity> consolidationNodeTemplateIdAndType =
        getAllConsolidationNodeTemplateIdAndType(unifiedComposotionDataList);
    for (UnifiedCompositionData unifiedCompositionData : unifiedComposotionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData =
          unifiedCompositionData.getComputeTemplateConsolidationData();
      String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
          computeTemplateConsolidationData.getNodeTemplateId());

      updNodeGetAttrInConnectivity(serviceTemplate, computeTemplateConsolidationData,
          computeTemplateConsolidationData.getNodeTemplateId(),
          newComputeNodeTemplateId, context, consolidationNodeTemplateIdAndType, false);

      String computeType =
          getComputeTypeSuffix(serviceTemplate,
              computeTemplateConsolidationData.getNodeTemplateId());

      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        String newPotNodeTemplateId =
            getNewPortNodeTemplateId(portTemplateConsolidationData.getNodeTemplateId(), computeType,
                computeTemplateConsolidationData);

        updNodeGetAttrInConnectivity(serviceTemplate, portTemplateConsolidationData,
            portTemplateConsolidationData.getNodeTemplateId(),
            newPotNodeTemplateId, context, consolidationNodeTemplateIdAndType, false);
      }
    }
  }

  protected void updNestedCompositionOutputParamGetAttrInConnectivity(
      ServiceTemplate serviceTemplate, List<UnifiedCompositionData> unifiedComposotionDataList,
      TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedComposotionDataList) {
      NestedTemplateConsolidationData nestedTemplateConsolidationData =
          unifiedCompositionData.getNestedTemplateConsolidationData();
      if (Objects.isNull(nestedTemplateConsolidationData)) {
        continue;
      }
      String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
      Optional<String> newNestedNodeTemplateId = context.getUnifiedNestedNodeTemplateId(
          serviceTemplateFileName, nestedTemplateConsolidationData.getNodeTemplateId());

      newNestedNodeTemplateId.ifPresent(
          newNestedNodeTemplateIdVal -> updOutputParamGetAttrInConnectivity(serviceTemplate,
              nestedTemplateConsolidationData, nestedTemplateConsolidationData.getNodeTemplateId(),
              newNestedNodeTemplateIdVal, context, true));
    }
  }

  protected void updNestedCompositionNodesGetAttrInConnectivity(
      ServiceTemplate serviceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      NestedTemplateConsolidationData nestedTemplateConsolidationData =
          unifiedCompositionData.getNestedTemplateConsolidationData();
      if (Objects.isNull(nestedTemplateConsolidationData)) {
        continue;
      }
      String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
      Optional<String> newNestedNodeTemplateId = context.getUnifiedNestedNodeTemplateId(
          serviceTemplateFileName, nestedTemplateConsolidationData.getNodeTemplateId());

      newNestedNodeTemplateId.ifPresent(
          newNestedNodeTemplateIdVal -> updNodeGetAttrInConnectivity(serviceTemplate,
              nestedTemplateConsolidationData, nestedTemplateConsolidationData.getNodeTemplateId(),
              newNestedNodeTemplateIdVal, context, null, true));
    }
  }

  private void updateRequirementForNodesConnectedIn(
      ServiceTemplate serviceTemplate,
      RequirementAssignmentData requirementAssignmentData,
      EntityConsolidationData entityConsolidationData,
      String originalNodeTemplateId,
      String newNodeTemplateId,
      TranslationContext context) {
    ToscaAnalyzerService toscaAnalyzerService = new ToscaAnalyzerServiceImpl();
    RequirementAssignment requirementAssignment = requirementAssignmentData
        .getRequirementAssignment();
    String newAbstractUnifiedNodeTemplateId = context.getUnifiedAbstractNodeTemplateId(
        serviceTemplate, entityConsolidationData.getNodeTemplateId());
    NodeTemplate abstractUnifiedNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
        newAbstractUnifiedNodeTemplateId);
    Optional<String> newCapabilityId = getNewCapabilityForNodesConnectedIn(serviceTemplate,
        abstractUnifiedNodeTemplate, requirementAssignment, newNodeTemplateId, context);
    if (newCapabilityId.isPresent()) {
      //Creating a copy of the requirement object and checking if it already exists in the
      // original node template
      RequirementAssignment requirementAssignmentCopy = (RequirementAssignment) getClonedObject(
          requirementAssignmentData.getRequirementAssignment(), RequirementAssignment.class);
      NodeTemplate originalNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
          originalNodeTemplateId);
      requirementAssignmentCopy.setCapability(newCapabilityId.get());
      requirementAssignmentCopy.setNode(newAbstractUnifiedNodeTemplateId);
      if (!toscaAnalyzerService.isRequirementExistInNodeTemplate(originalNodeTemplate,
          requirementAssignmentData.getRequirementId(), requirementAssignmentCopy)) {
        //Update the existing requirement
        requirementAssignmentData.getRequirementAssignment().setCapability(newCapabilityId
            .get());
        requirementAssignmentData.getRequirementAssignment()
            .setNode(newAbstractUnifiedNodeTemplateId);
      } else {
        //The updated requirement already exists in the node template so simply remove the
        // current one
        DataModelUtil.removeRequirementAssignment(originalNodeTemplate, requirementAssignmentData
            .getRequirementId(), requirementAssignmentData.getRequirementAssignment());
      }
    }
  }

  private void updateRequirementForNestedCompositionNodesConnectedIn(
      ServiceTemplate serviceTemplate,
      RequirementAssignmentData requirementAssignmentData,
      EntityConsolidationData entityConsolidationData,
      String newNodeTemplateId,
      TranslationContext context) {
    ToscaAnalyzerService toscaAnalyzerService = new ToscaAnalyzerServiceImpl();
    String newAbstractUnifiedNodeTemplateId = newNodeTemplateId;
    RequirementAssignment requirementAssignment = requirementAssignmentData
        .getRequirementAssignment();
    //Creating a copy of the requirement object and checking if it already exists in the
    // original node template
    RequirementAssignment requirementAssignmentCopy = (RequirementAssignment) getClonedObject(
        requirementAssignmentData.getRequirementAssignment(), RequirementAssignment.class);
    NodeTemplate unifiedAbstractNestedNodeTemplate = DataModelUtil
        .getNodeTemplate(serviceTemplate, newAbstractUnifiedNodeTemplateId);
    requirementAssignmentCopy.setCapability(requirementAssignment.getCapability());
    requirementAssignmentCopy.setNode(newAbstractUnifiedNodeTemplateId);
    if (!toscaAnalyzerService.isRequirementExistInNodeTemplate(unifiedAbstractNestedNodeTemplate,
        requirementAssignmentData.getRequirementId(), requirementAssignmentCopy)) {
      //Update the existing requirement
      requirementAssignmentData.getRequirementAssignment()
          .setNode(newAbstractUnifiedNodeTemplateId);
    } else {
      //The updated requirement already exists in the node template so simply remove the
      // current one
      DataModelUtil.removeRequirementAssignment(unifiedAbstractNestedNodeTemplate,
          requirementAssignmentData.getRequirementId(), requirementAssignmentData
              .getRequirementAssignment());
    }
  }

  private Optional<String> getNewCapabilityForNodesConnectedIn(ServiceTemplate serviceTemplate,
                                                               NodeTemplate unifiedNodeTemplate,
                                                               RequirementAssignment
                                                                   requirementAssignment,
                                                               String newNodeTemplateId,
                                                               TranslationContext context) {
    ServiceTemplate globalSubstitutionServiceTemplate =
        HeatToToscaUtil.fetchGlobalSubstitutionServiceTemplate(serviceTemplate, context);
    Map<String, NodeType> nodeTypes = globalSubstitutionServiceTemplate.getNode_types();
    String unifiedNodeTemplateType = unifiedNodeTemplate.getType();
    NodeType unifiedNodeType = nodeTypes.get(unifiedNodeTemplateType);
    Map<String, CapabilityDefinition> abstractNodeTypeCapabilities = unifiedNodeType
        .getCapabilities();
    for (Map.Entry<String, CapabilityDefinition> entry : abstractNodeTypeCapabilities.entrySet()) {
      String capabilityId = entry.getKey();
      CapabilityDefinition capabilityDefinition = entry.getValue();
      String capabilityType = capabilityDefinition.getType();
      if (capabilityType.equals(requirementAssignment.getCapability())) {
        //Matching capability type found..Check if the id ends with new node template id
        if (capabilityId.endsWith(newNodeTemplateId)) {
          return Optional.ofNullable(capabilityId);
        }
      }
    }
    return Optional.empty();
  }


  private void updateRequirementInAbstractNodeTemplate(ServiceTemplate serviceTemplate,
                                                       EntityConsolidationData
                                                           entityConsolidationData,
                                                       String newNodeTemplateId,
                                                       Map<String, List<RequirementAssignmentData>>
                                                           requirementAssignmentDataMap,
                                                       TranslationContext context) {
    ToscaAnalyzerService toscaAnalyzerService = new ToscaAnalyzerServiceImpl();
    for (Map.Entry<String, List<RequirementAssignmentData>> entry : requirementAssignmentDataMap
        .entrySet()) {
      String abstractNodeTemplateId = context.getUnifiedAbstractNodeTemplateId(
          serviceTemplate, entityConsolidationData.getNodeTemplateId());
      NodeTemplate abstractNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
          abstractNodeTemplateId);
      if (abstractNodeTemplate == null) {
        //The abstract node template is not found from id in the context
        return;
      }
      List<RequirementAssignmentData> requirementAssignmentDataList = entry.getValue();
      for (RequirementAssignmentData requirementAssignmentData : requirementAssignmentDataList) {
        String oldRequirementId = requirementAssignmentData.getRequirementId();
        RequirementAssignment abstractRequirementAssignment = (RequirementAssignment)
            getClonedObject(requirementAssignmentData.getRequirementAssignment(),
                RequirementAssignment.class);
        String newRequirementId = oldRequirementId + "_" + newNodeTemplateId;
        //Check if the requirement is not already present in the list of requirements of the
        // abstract node template
        if (!toscaAnalyzerService.isRequirementExistInNodeTemplate(abstractNodeTemplate,
            newRequirementId, abstractRequirementAssignment)) {
          DataModelUtil.addRequirementAssignment(abstractNodeTemplate, newRequirementId,
              abstractRequirementAssignment);
          //Update the volume relationship template if required
          updateVolumeRelationshipTemplate(serviceTemplate, abstractRequirementAssignment
              .getRelationship(), context);
        }
      }
    }
  }

  private NodeTemplate getAbstractNodeTemplate(
      ServiceTemplate serviceTemplate,
      UnifiedCompositionEntity unifiedCompositionEntity,
      ComputeTemplateConsolidationData computeTemplateConsolidationData,
      PortTemplateConsolidationData portTemplateConsolidationData,
      TranslationContext context) {
    String abstractNodeTemplateId =
        getAbstractNodeTemplateId(serviceTemplate, unifiedCompositionEntity,
            computeTemplateConsolidationData, portTemplateConsolidationData, context);

    return DataModelUtil.getNodeTemplate(serviceTemplate,
        abstractNodeTemplateId);
  }

  private String getAbstractNodeTemplateId(
      ServiceTemplate serviceTemplate,
      UnifiedCompositionEntity unifiedCompositionEntity,
      ComputeTemplateConsolidationData computeTemplateConsolidationData,
      PortTemplateConsolidationData portTemplateConsolidationData,
      TranslationContext context) {
    switch (unifiedCompositionEntity) {
      case Compute:
        return context.getUnifiedAbstractNodeTemplateId(serviceTemplate,
            computeTemplateConsolidationData.getNodeTemplateId());
      case Port:
        return context.getUnifiedAbstractNodeTemplateId(serviceTemplate,
            portTemplateConsolidationData.getNodeTemplateId());
      default:
        return null;
    }
  }

  private void updNodeGetAttrInConnectivity(
      ServiceTemplate serviceTemplate,
      EntityConsolidationData entityConsolidationData,
      String oldNodeTemplateId, String newNodeTemplateId,
      TranslationContext context,
      Map<String, UnifiedCompositionEntity> consolidationNodeTemplateIdAndType,
      boolean isNested) {
    Map<String, List<GetAttrFuncData>> nodesGetAttrIn = entityConsolidationData.getNodesGetAttrIn();
    if (MapUtils.isEmpty(nodesGetAttrIn)) {
      return;
    }

    for (String sourceNodeTemplateId : nodesGetAttrIn.keySet()) {
      NodeTemplate sourceNodeTemplate =
          DataModelUtil.getNodeTemplate(serviceTemplate, sourceNodeTemplateId);
      if (!isNested && consolidationNodeTemplateIdAndType.keySet().contains(sourceNodeTemplateId)) {
        continue;
      }
      List<GetAttrFuncData> getAttrFuncDataList = nodesGetAttrIn.get(sourceNodeTemplateId);
      for (GetAttrFuncData getAttrFuncData : getAttrFuncDataList) {
        Object propertyValue =
            DataModelUtil.getPropertyValue(sourceNodeTemplate, getAttrFuncData.getFieldName());
        String newAttrName = null;
        String newGetAttrAbstractNodeTemplateId = newNodeTemplateId;
        if (!isNested) {
          newGetAttrAbstractNodeTemplateId =
              context.getUnifiedAbstractNodeTemplateId(serviceTemplate, oldNodeTemplateId);
          newAttrName = getNewSubstitutionOutputParameterId(newNodeTemplateId, getAttrFuncData
              .getAttributeName());
        }
        List<List<Object>> getAttrFuncValueList = extractGetAttrFunction(propertyValue);
        updateGetAttrValue(oldNodeTemplateId, getAttrFuncData, newGetAttrAbstractNodeTemplateId,
            newAttrName, getAttrFuncValueList, isNested);
      }
    }
  }

  private void updateGetAttrValue(String oldNodeTemplateId, GetAttrFuncData getAttrFuncData,
                                  String newNodeTemplateId, String newAttrName,
                                  List<List<Object>> getAttrFuncValueList, boolean isNested) {
    for (List<Object> getAttrFuncValue : getAttrFuncValueList) {
      if (oldNodeTemplateId.equals(getAttrFuncValue.get(0))
          && getAttrFuncData.getAttributeName().equals(getAttrFuncValue.get(1))) {
        getAttrFuncValue.set(0, newNodeTemplateId);
        if (!isNested) {
          getAttrFuncValue.set(1, newAttrName);
        }
      }
    }
  }

  private String getTemplateName(ServiceTemplate serviceTemplate,
                                 UnifiedCompositionData unifiedCompositionData,
                                 Integer index) {
    ComputeTemplateConsolidationData computeTemplateConsolidationData =
        unifiedCompositionData.getComputeTemplateConsolidationData();
    String computeType =
        getComputeTypeSuffix(serviceTemplate, computeTemplateConsolidationData.getNodeTemplateId());
    String templateName = "Nested_" + computeType;
    if (Objects.nonNull(index)) {
      templateName = templateName + "_" + index.toString();
    }
    return templateName;
  }

  private String getComputeTypeSuffix(ServiceTemplate serviceTemplate,
                                      String computeNodeTemplateId) {
    NodeTemplate computeNodeTemplate =
        DataModelUtil.getNodeTemplate(serviceTemplate, computeNodeTemplateId);
    return getComputeTypeSuffix(computeNodeTemplate.getType());
  }

  /**
   * Gets compute type.
   *
   * @param computeType the compute node type abc.def.vFSB
   * @return the compute type e.g.:vFSB
   */
  private String getComputeTypeSuffix(String computeType) {
    return DataModelUtil.getNamespaceSuffix(computeType);
  }

  private void updOutputParamGetAttrInConnectivity(ServiceTemplate serviceTemplate,
                                                   EntityConsolidationData entityConsolidationData,
                                                   String oldNodeTemplateId,
                                                   String newNodeTemplateId,
                                                   TranslationContext context,
                                                   boolean isNested) {
    List<GetAttrFuncData> outputParametersGetAttrIn =
        entityConsolidationData.getOutputParametersGetAttrIn();
    if (CollectionUtils.isEmpty(outputParametersGetAttrIn)) {
      return;
    }
    for (GetAttrFuncData getAttrFuncData : outputParametersGetAttrIn) {
      Object outputParamValue =
          DataModelUtil.getOuputParameter(serviceTemplate, getAttrFuncData.getFieldName())
              .getValue();
      String newAttrName = null;
      String newGetAttrAbstractNodeTemplateId = newNodeTemplateId;
      if (!isNested) {
        newGetAttrAbstractNodeTemplateId =
            context.getUnifiedAbstractNodeTemplateId(serviceTemplate, oldNodeTemplateId);
        newAttrName = getNewSubstitutionOutputParameterId(newNodeTemplateId, getAttrFuncData
            .getAttributeName());
      }
      List<List<Object>> getAttrFuncValueList = extractGetAttrFunction(outputParamValue);
      updateGetAttrValue(oldNodeTemplateId, getAttrFuncData, newGetAttrAbstractNodeTemplateId,
          newAttrName,
          getAttrFuncValueList, isNested);
    }

  }

  private List<List<Object>> extractGetAttrFunction(Object valueObject) {

    List<List<Object>> getAttrValueList = new ArrayList<>();

    if (valueObject instanceof Map) {
      if (((Map) valueObject).containsKey(ToscaFunctions.GET_ATTRIBUTE.getDisplayName())) {
        getAttrValueList.add(
            (List<Object>) ((Map) valueObject).get(ToscaFunctions.GET_ATTRIBUTE.getDisplayName()));
      }

      for (Object key : ((Map) valueObject).keySet()) {
        getAttrValueList.addAll(extractGetAttrFunction(((Map) valueObject).get(key)));
      }


    } else if (valueObject instanceof List) {
      for (Object valueEntity : (List) valueObject) {
        getAttrValueList.addAll(extractGetAttrFunction(valueEntity));
      }
    }
    return getAttrValueList;
  }

  private boolean isIncludeGetInputFunc(Object valueObject) {
    if (valueObject instanceof Map) {
      if (((Map) valueObject).containsKey(ToscaFunctions.GET_INPUT.getDisplayName())) {
        return true;
      }
      Map.Entry<String, Object> functionMapEntry =
          (Map.Entry<String, Object>) ((Map) valueObject).entrySet().iterator().next();
      return isIncludeGetInputFunc(functionMapEntry.getValue());

    } else if (valueObject instanceof List) {
      for (Object valueEntity : (List) valueObject) {
        if (isIncludeGetInputFunc(valueEntity) == true) {
          return true;
        }
      }
    }
    return false;
  }

  private void createOutputParameters(ServiceTemplate serviceTemplate,
                                      ServiceTemplate substitutionServiceTemplate,
                                      List<UnifiedCompositionData> unifiedCompositionDataList,
                                      String computeNodeType) {

    createOutputParametersForCompute(serviceTemplate, substitutionServiceTemplate,
        unifiedCompositionDataList);
    createOutputParameterForPorts(serviceTemplate, substitutionServiceTemplate,
        unifiedCompositionDataList, computeNodeType);
  }

  private void createOutputParameterForPorts(
      ServiceTemplate serviceTemplate,
      ServiceTemplate substitutionServiceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      String connectedComputeNodeType) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      if (CollectionUtils.isEmpty(portTemplateConsolidationDataList)) {
        return;
      }

      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        String newPortNodeTemplateId =
            getNewPortNodeTemplateId(portTemplateConsolidationData.getNodeTemplateId(),
                connectedComputeNodeType,
                unifiedCompositionData.getComputeTemplateConsolidationData());
        addOutputParameters(portTemplateConsolidationData, newPortNodeTemplateId,
            substitutionServiceTemplate, unifiedCompositionDataList);
      }
    }
  }

  //The ID should be <vm_type>_<port_type> or <vm_type>_<portNodeTemplateId>
  private String getNewPortNodeTemplateId(
      String portNodeTemplateId,
      String connectedComputeNodeType,
      ComputeTemplateConsolidationData computeTemplateConsolidationData) {

    StringBuilder newPortNodeTemplateId = new StringBuilder();
    String portType = ConsolidationDataUtil.getPortType(portNodeTemplateId);
    newPortNodeTemplateId.append(DataModelUtil.getNamespaceSuffix(connectedComputeNodeType));
    if (computeTemplateConsolidationData.getPorts().get(portType).size() > 1) {
      //single port
      newPortNodeTemplateId.append("_").append(portNodeTemplateId);
    } else {
      //consolidation port
      newPortNodeTemplateId.append("_").append(portType);
    }
    return newPortNodeTemplateId.toString();
  }

  private void createOutputParametersForCompute(
      ServiceTemplate serviceTemplate,
      ServiceTemplate substitutionServiceTemplate,
      List<UnifiedCompositionData>
          unifiedCompositionDataList) {
    List<EntityConsolidationData> computeConsolidationDataList =
        getComputeConsolidationDataList(unifiedCompositionDataList);

    for (EntityConsolidationData computeTemplateConsolidationData : computeConsolidationDataList) {
      String newComputeNodeTemplateId =
          getNewComputeNodeTemplateId(serviceTemplate,
              computeTemplateConsolidationData.getNodeTemplateId());
      addOutputParameters(computeTemplateConsolidationData, newComputeNodeTemplateId,
          substitutionServiceTemplate, unifiedCompositionDataList);
    }
  }

  private void addOutputParameters(EntityConsolidationData entityConsolidationData,
                                   String newNodeTemplateId,
                                   ServiceTemplate substitutionServiceTemplate,
                                   List<UnifiedCompositionData> unifiedCompositionDataList) {
    handleNodesGetAttrIn(entityConsolidationData, newNodeTemplateId, substitutionServiceTemplate,
        unifiedCompositionDataList);

    handleOutputParamGetAttrIn(entityConsolidationData, newNodeTemplateId,
        substitutionServiceTemplate);
  }

  private void handleOutputParamGetAttrIn(EntityConsolidationData entityConsolidationData,
                                          String newNodeTemplateId,
                                          ServiceTemplate substitutionServiceTemplate) {
    List<GetAttrFuncData> outputParametersGetAttrIn =
        entityConsolidationData.getOutputParametersGetAttrIn();
    if (!CollectionUtils.isEmpty(outputParametersGetAttrIn)) {
      for (GetAttrFuncData getAttrFuncData : outputParametersGetAttrIn) {
        createAndAddOutputParameter(entityConsolidationData, newNodeTemplateId,
            substitutionServiceTemplate, getAttrFuncData);
      }
    }
  }

  private void handleNodesGetAttrIn(EntityConsolidationData entityConsolidationData,
                                    String newNodeTemplateId,
                                    ServiceTemplate substitutionServiceTemplate,
                                    List<UnifiedCompositionData> unifiedCompositionDataList) {
    Map<String, List<GetAttrFuncData>> getAttrIn = entityConsolidationData.getNodesGetAttrIn();

    if (!MapUtils.isEmpty(getAttrIn)) {
      Map<String, UnifiedCompositionEntity> consolidationNodeTemplateIdAndType =
          getAllConsolidationNodeTemplateIdAndType(unifiedCompositionDataList);
      for (String sourceNodeTemplateId : getAttrIn.keySet()) {
        if (!consolidationNodeTemplateIdAndType.keySet().contains(sourceNodeTemplateId)) {
          List<GetAttrFuncData> getAttrFuncDataList = getAttrIn.get(sourceNodeTemplateId);
          for (GetAttrFuncData getAttrFuncData : getAttrFuncDataList) {
            createAndAddOutputParameter(entityConsolidationData, newNodeTemplateId,
                substitutionServiceTemplate, getAttrFuncData);
          }
        }
      }
    }
  }

  private void createAndAddOutputParameter(EntityConsolidationData entityConsolidationData,
                                           String newNodeTemplateId,
                                           ServiceTemplate substitutionServiceTemplate,
                                           GetAttrFuncData getAttrFuncData) {
    Map<String, List<Object>> parameterValue = new HashMap<>();
    List<Object> valueList = new ArrayList<>();
    valueList.add(newNodeTemplateId);
    valueList.add(getAttrFuncData.getAttributeName());
    parameterValue.put(ToscaFunctions.GET_ATTRIBUTE.getDisplayName(), valueList);
    ParameterDefinition outputParameter = new ParameterDefinition();
    outputParameter.setValue(parameterValue);
    //todo - the type need to be change based on the attribute type in the accordingly node type
    // of this node template - XXX, instead of list of string, list of XXXX,
    outputParameter.setType(PropertyType.LIST.getDisplayName());
    outputParameter.setEntry_schema(
        DataModelUtil.createEntrySchema(PropertyType.STRING.getDisplayName(), null, null));
    DataModelUtil.addOutputParameterToTopologyTemplate(substitutionServiceTemplate,
        getNewSubstitutionOutputParameterId(newNodeTemplateId, getAttrFuncData.getAttributeName()),
        outputParameter);
  }

  private String getNewSubstitutionOutputParameterId(String newNodeTemplateId,
                                                     String attributeName) {
    return newNodeTemplateId + "_" + attributeName;
  }

  private void addUnifiedSubstitionData(TranslationContext context, ServiceTemplate
      serviceTemplate, List<UnifiedCompositionData> unifiedCompositionDataList, String
                                            substituteNodeTemplateId) {
    String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      //Add compute node template mapping information
      ComputeTemplateConsolidationData computeTemplateConsolidationData =
          unifiedCompositionData.getComputeTemplateConsolidationData();
      String computeNodeTemplateId = computeTemplateConsolidationData.getNodeTemplateId();
      context.addUnifiedSubstitutionData(serviceTemplateFileName, computeNodeTemplateId,
          substituteNodeTemplateId);
      //Add Port template mapping information
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);

      if (CollectionUtils.isNotEmpty(portTemplateConsolidationDataList)) {
        for (PortTemplateConsolidationData portTemplateConsolidationData :
            portTemplateConsolidationDataList) {
          String oldPortNodeTemplateId = portTemplateConsolidationData.getNodeTemplateId();
          context.addUnifiedSubstitutionData(serviceTemplateFileName, oldPortNodeTemplateId,
              substituteNodeTemplateId);
        }
      }
    }
  }

  private void addSubstitutionFilteringProperty(String templateName, NodeTemplate nodeTemplate,
                                                int count) {
    Map<String, Object> serviceTemplateFilterPropertyValue = new HashMap<>();
    Map<String, Object> properties = nodeTemplate.getProperties();
    serviceTemplateFilterPropertyValue.put(ToscaConstants
        .SUBSTITUTE_SERVICE_TEMPLATE_PROPERTY_NAME, templateName);
    serviceTemplateFilterPropertyValue.put(ToscaConstants.COUNT_PROPERTY_NAME, count);
    properties.put(ToscaConstants.SERVICE_TEMPLATE_FILTER_PROPERTY_NAME,
        serviceTemplateFilterPropertyValue);
    nodeTemplate.setProperties(properties);
  }

  private void addIndexValueProperty(NodeTemplate nodeTemplate) {
    List<String> indexValueGetPropertyValue = new ArrayList<>();
    indexValueGetPropertyValue.add(ToscaConstants.MODELABLE_ENTITY_NAME_SELF);
    indexValueGetPropertyValue.add(ToscaConstants.SERVICE_TEMPLATE_FILTER_PROPERTY_NAME);
    indexValueGetPropertyValue.add(ToscaConstants.INDEX_VALUE_PROPERTY_NAME);

    Map<String, Object> indexPropertyValue = new HashMap<>();
    Map<String, Object> properties = nodeTemplate.getProperties();
    indexPropertyValue.put(ToscaFunctions.GET_PROPERTY.getDisplayName(),
        indexValueGetPropertyValue);
    properties.put(ToscaConstants.INDEX_VALUE_PROPERTY_NAME,
        indexPropertyValue);
    nodeTemplate.setProperties(properties);
  }

  private String getSubstituteNodeTemplateId(ServiceTemplate serviceTemplate,
                                             UnifiedCompositionData unifiedCompositionData,
                                             Integer index) {
    String computeNodeTemplateId =
        unifiedCompositionData.getComputeTemplateConsolidationData().getNodeTemplateId();
    NodeTemplate computeNodeTemplate =
        DataModelUtil.getNodeTemplate(serviceTemplate, computeNodeTemplateId);
    String nodeTemplateId = ABSTRACT_NODE_TEMPLATE_ID_PREFIX + DataModelUtil
        .getNamespaceSuffix(computeNodeTemplate.getType());
    if (Objects.nonNull(index)) {
      nodeTemplateId = nodeTemplateId + "_" + index.toString();
    }
    return nodeTemplateId;
  }

  private String getSubstitutionNodeTypeId(ServiceTemplate serviceTemplate,
                                           UnifiedCompositionData unifiedCompositionData,
                                           Integer index) {
    String computeNodeTemplateId =
        unifiedCompositionData.getComputeTemplateConsolidationData().getNodeTemplateId();
    NodeTemplate computeNodeTemplate =
        DataModelUtil.getNodeTemplate(serviceTemplate, computeNodeTemplateId);
    String nodeTypeId = ToscaNodeType.ABSTRACT_NODE_TYPE_PREFIX
        + DataModelUtil.getNamespaceSuffix(computeNodeTemplate.getType());
    if (Objects.nonNull(index)) {
      nodeTypeId = nodeTypeId + "_" + index.toString();
    }
    return nodeTypeId;
  }

  private String getNewComputeNodeTemplateId(
      ServiceTemplate serviceTemplate,
      String computeNodeTemplateId) {
    return getComputeTypeSuffix(serviceTemplate, computeNodeTemplateId);
  }

  private NodeType handleSubstitutionGlobalNodeType(ServiceTemplate serviceTemplate,
                                                    ServiceTemplate substitutionServiceTemplate,
                                                    TranslationContext context,
                                                    UnifiedCompositionData unifiedCompositionData,
                                                    Integer index) {
    String substitutionNodeTypeId =
        getSubstitutionNodeTypeId(serviceTemplate, unifiedCompositionData, index);
    NodeType substitutionNodeType = new ToscaAnalyzerServiceImpl()
        .createInitSubstitutionNodeType(substitutionServiceTemplate,
            ToscaNodeType.VFC_ABSTRACT_SUBSTITUTE);
    ServiceTemplate globalSubstitutionServiceTemplate =
        HeatToToscaUtil.fetchGlobalSubstitutionServiceTemplate(serviceTemplate, context);
    DataModelUtil.addNodeType(globalSubstitutionServiceTemplate, substitutionNodeTypeId,
        substitutionNodeType);

    return substitutionNodeType;
  }

  private void handlePorts(ServiceTemplate serviceTemplate,
                           ServiceTemplate substitutionServiceTemplate,
                           List<UnifiedCompositionData> unifiedCompositionDataList,
                           String connectedComputeNodeType,
                           TranslationContext context) {

    if (unifiedCompositionDataList.size() > 1) {
      handleConsolidationPorts(serviceTemplate, substitutionServiceTemplate,
          unifiedCompositionDataList, connectedComputeNodeType, context);
    } else {
      handleSinglePorts(serviceTemplate, substitutionServiceTemplate, connectedComputeNodeType,
          unifiedCompositionDataList, context);
    }
  }

  private void handleSinglePorts(ServiceTemplate serviceTemplate,
                                 ServiceTemplate substitutionServiceTemplate,
                                 String connectedComputeNodeType,
                                 List<UnifiedCompositionData> unifiedCompositionDataList,
                                 TranslationContext context) {
    UnifiedCompositionData unifiedCompositionData = unifiedCompositionDataList.get(0);
    List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
        getPortTemplateConsolidationDataList(unifiedCompositionData);
    if (CollectionUtils.isEmpty(portTemplateConsolidationDataList)) {
      return;
    }
    for (PortTemplateConsolidationData portTemplateConsolidationData :
        portTemplateConsolidationDataList) {
      List<EntityConsolidationData> portConsolidationDataList = new ArrayList<>();
      portConsolidationDataList.add(portTemplateConsolidationData);
      handlePortNodeTemplate(serviceTemplate, substitutionServiceTemplate,
          portConsolidationDataList, connectedComputeNodeType,
          unifiedCompositionData.getComputeTemplateConsolidationData(),
          unifiedCompositionDataList, context);
    }
  }

  private void handleConsolidationPorts(ServiceTemplate serviceTemplate,
                                        ServiceTemplate substitutionServiceTemplate,
                                        List<UnifiedCompositionData> unifiedCompositionDataList,
                                        String connectedComputeNodeType,
                                        TranslationContext context) {
    Collection<ComputeTemplateConsolidationData> computeConsolidationDataList =
        (Collection) getComputeConsolidationDataList(unifiedCompositionDataList);

    Map<String, Set<String>> portIdsPerPortType = UnifiedCompositionUtil
        .collectAllPortsFromEachTypesFromComputes(computeConsolidationDataList);

    for (String portType : portIdsPerPortType.keySet()) {
      List<EntityConsolidationData> portTemplateConsolidationDataList =
          getPortConsolidationDataList(portIdsPerPortType.get(portType),
              unifiedCompositionDataList);
      if (CollectionUtils.isEmpty(portTemplateConsolidationDataList)) {
        continue;
      }

      handlePortNodeTemplate(serviceTemplate, substitutionServiceTemplate,
          portTemplateConsolidationDataList, connectedComputeNodeType,
          unifiedCompositionDataList.get(0).getComputeTemplateConsolidationData(),
          unifiedCompositionDataList, context);
    }
  }

  private void handlePortNodeTemplate(
      ServiceTemplate serviceTemplate,
      ServiceTemplate substitutionServiceTemplate,
      List<EntityConsolidationData> portTemplateConsolidationDataList,
      String connectedComputeNodeType,
      ComputeTemplateConsolidationData computeTemplateConsolidationData,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context) {
    EntityConsolidationData portTemplateConsolidationData =
        portTemplateConsolidationDataList.get(0);
    NodeTemplate newPortNodeTemplate = getNodeTemplate(
        portTemplateConsolidationData.getNodeTemplateId(), serviceTemplate, context).clone();

    removeConnectivityOut(portTemplateConsolidationData, newPortNodeTemplate);
    handleProperties(serviceTemplate, newPortNodeTemplate, substitutionServiceTemplate,
        UnifiedCompositionEntity.Port, portTemplateConsolidationDataList,
        computeTemplateConsolidationData, unifiedCompositionDataList, context);

    String newPortNodeTemplateId =
        getNewPortNodeTemplateId(portTemplateConsolidationData
                .getNodeTemplateId(), connectedComputeNodeType,
            computeTemplateConsolidationData);
    //Update requirements for relationships between the consolidation entities
    handleConsolidationEntitiesRequirementConnectivity(newPortNodeTemplateId, newPortNodeTemplate,
        substitutionServiceTemplate, context);
    DataModelUtil.addNodeTemplate(substitutionServiceTemplate, newPortNodeTemplateId,
        newPortNodeTemplate);

    //Add the node template mapping in the context for handling requirement updation
    context.addSubstitutionServiceTemplateUnifiedSubstitutionData(ToscaUtil
            .getServiceTemplateFileName(substitutionServiceTemplate),
        portTemplateConsolidationData.getNodeTemplateId(), newPortNodeTemplateId);
  }


  private NodeTemplate getNodeTemplate(String nodeTemplateId, ServiceTemplate serviceTemplate,
                                       TranslationContext context) {

    NodeTemplate nodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate, nodeTemplateId);

    if (Objects.isNull(nodeTemplate)) {
      nodeTemplate = context
          .getCleanedNodeTemplate(ToscaUtil.getServiceTemplateFileName(serviceTemplate),
              nodeTemplateId);

    }
    return nodeTemplate;
  }


  private String handleCompute(ServiceTemplate serviceTemplate,
                               ServiceTemplate substitutionServiceTemplate,
                               List<UnifiedCompositionData> unifiedCompositionDataList,
                               TranslationContext context) {
    ComputeTemplateConsolidationData computeTemplateConsolidationData =
        unifiedCompositionDataList.get(0).getComputeTemplateConsolidationData();
    handleComputeNodeTemplate(serviceTemplate, substitutionServiceTemplate,
        unifiedCompositionDataList, context);
    return handleComputeNodeType(serviceTemplate, substitutionServiceTemplate,
        computeTemplateConsolidationData);
  }

  private String handleComputeNodeType(
      ServiceTemplate serviceTemplate,
      ServiceTemplate substitutionServiceTemplate,
      ComputeTemplateConsolidationData computeTemplateConsolidationData) {
    NodeTemplate computeNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
        computeTemplateConsolidationData.getNodeTemplateId());
    NodeType computeNodeType =
        DataModelUtil.getNodeType(serviceTemplate, computeNodeTemplate.getType());
    DataModelUtil
        .addNodeType(substitutionServiceTemplate, computeNodeTemplate.getType(), computeNodeType);

    return computeNodeTemplate.getType();
  }

  private void handleComputeNodeTemplate(ServiceTemplate serviceTemplate,
                                         ServiceTemplate substitutionServiceTemplate,
                                         List<UnifiedCompositionData> unifiedCompositionDataList,
                                         TranslationContext context) {
    ComputeTemplateConsolidationData computeTemplateConsolidationData =
        unifiedCompositionDataList.get(0).getComputeTemplateConsolidationData();
    NodeTemplate newComputeNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
        computeTemplateConsolidationData.getNodeTemplateId()).clone();

    removeConnectivityOut(computeTemplateConsolidationData, newComputeNodeTemplate);
    removeVolumeConnectivity(computeTemplateConsolidationData, newComputeNodeTemplate);

    List<EntityConsolidationData> computeConsoliadtionDataList =
        getComputeConsolidationDataList(unifiedCompositionDataList);

    handleProperties(serviceTemplate, newComputeNodeTemplate, substitutionServiceTemplate,
        UnifiedCompositionEntity.Compute, computeConsoliadtionDataList,
        computeTemplateConsolidationData, unifiedCompositionDataList, context);

    String newComputeNodeTemplateId = getNewComputeNodeTemplateId(serviceTemplate,
        computeTemplateConsolidationData.getNodeTemplateId());
    //Update requirements for relationships between the consolidation entities
    handleConsolidationEntitiesRequirementConnectivity(newComputeNodeTemplateId,
        newComputeNodeTemplate,
        substitutionServiceTemplate, context);
    DataModelUtil
        .addNodeTemplate(substitutionServiceTemplate,
            newComputeNodeTemplateId, newComputeNodeTemplate);
    //Add the node template mapping in the context for handling requirement updation
    context.addSubstitutionServiceTemplateUnifiedSubstitutionData(ToscaUtil
            .getServiceTemplateFileName(substitutionServiceTemplate),
        computeTemplateConsolidationData.getNodeTemplateId(), newComputeNodeTemplateId);

  }

  private List<EntityConsolidationData> getComputeConsolidationDataList(
      List<UnifiedCompositionData> unifiedCompositionDataList) {
    List<EntityConsolidationData> computeConsolidationDataList = new ArrayList<>();
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      computeConsolidationDataList
          .add(unifiedCompositionData.getComputeTemplateConsolidationData());
    }
    return computeConsolidationDataList;
  }


  private void handleProperties(ServiceTemplate serviceTemplate, NodeTemplate nodeTemplate,
                                ServiceTemplate substitutionServiceTemplate,
                                UnifiedCompositionEntity unifiedCompositionEntity,
                                List<EntityConsolidationData> entityConsolidationDataList,
                                ComputeTemplateConsolidationData computeTemplateConsolidationData,
                                List<UnifiedCompositionData> unifiedCompositionDataList,
                                TranslationContext context) {
    List<String> propertiesWithIdenticalVal = consolidationService.getPropertiesWithIdenticalVal();
    nodeTemplate.setProperties(new HashedMap());

    for (EntityConsolidationData entityConsolidationData : entityConsolidationDataList) {
      String nodeTemplateId = entityConsolidationData.getNodeTemplateId();
      Map<String, Object> properties =
          DataModelUtil.getNodeTemplateProperties(serviceTemplate, nodeTemplateId);
      if (MapUtils.isEmpty(properties)) {
        continue;
      }

      for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
        if (propertiesWithIdenticalVal.contains(propertyEntry.getKey())) {
          String parameterId = updateIdenticalProperty(nodeTemplate, propertyEntry.getKey());
          addInputParameter(parameterId, PropertyType.STRING.getDisplayName(), null,
              substitutionServiceTemplate);
        } else {
          Optional<String> parameterId =
              updateProperty(serviceTemplate, nodeTemplateId, nodeTemplate, propertyEntry,
                  unifiedCompositionEntity, computeTemplateConsolidationData,
                  unifiedCompositionDataList,
                  context);

          //todo - define list of type which will match the node property type (instead of string)
          NodeType nodeTypeWithFlatHierarchy =
              HeatToToscaUtil.getNodeTypeWithFlatHierarchy(nodeTemplate.getType(), serviceTemplate,
                  context);
          String propertyType =
              nodeTypeWithFlatHierarchy.getProperties().get(propertyEntry.getKey())
                  .getType();

          if (propertyType.equalsIgnoreCase(PropertyType.STRING.getDisplayName())
              || propertyType.equalsIgnoreCase(PropertyType.INTEGER.getDisplayName())
              || propertyType.equalsIgnoreCase(PropertyType.FLOAT.getDisplayName())
              || propertyType.equalsIgnoreCase(PropertyType.BOOLEAN.getDisplayName())) {
            parameterId
                .ifPresent(parameterIdValue -> addInputParameter(parameterIdValue,
                    PropertyType.LIST.getDisplayName(),
                    DataModelUtil
                        .createEntrySchema(propertyType.toLowerCase(), null, null),
                    substitutionServiceTemplate));
          } else {
            parameterId
                .ifPresent(parameterIdValue -> addInputParameter(parameterIdValue,
                    PropertyType.LIST.getDisplayName(),
                    DataModelUtil
                        .createEntrySchema(PropertyTypeExt.JSON.getDisplayName(), null, null),
                    substitutionServiceTemplate));
          }
        }
      }
    }
  }

  private void handleConsolidationEntitiesRequirementConnectivity(String nodeTemplateId,
                                                                  NodeTemplate nodeTemplate,
                                                                  ServiceTemplate
                                                                      substitutionServiceTemplate,
                                                                  TranslationContext context) {
    Map<String, RequirementAssignment> updatedNodeTemplateRequirements = new HashMap<>();
    List<Map<String, RequirementAssignment>> nodeTemplateRequirements = DataModelUtil
        .getNodeTemplateRequirementList(nodeTemplate);
    if (CollectionUtils.isEmpty(nodeTemplateRequirements)) {
      return;
    }

    for (Map<String, RequirementAssignment> requirement : nodeTemplateRequirements) {
      for (Map.Entry<String, RequirementAssignment> entry : requirement.entrySet()) {
        RequirementAssignment requirementAssignment = entry.getValue();
        String requirementNode = requirementAssignment.getNode();
        String unifiedNodeTemplateId =
            context.getUnifiedSubstitutionNodeTemplateId(substitutionServiceTemplate,
                requirementNode);
        if (unifiedNodeTemplateId != null) {
          //Update the node id in the requirement
          requirementAssignment.setNode(unifiedNodeTemplateId);
        }
      }
    }
    nodeTemplate.setRequirements(nodeTemplateRequirements);
  }

  /**
   * Update the node references in the volume relationship templates.
   *
   * @param serviceTemplate the service template
   * @param context         the context
   */
  private void updateVolumeRelationshipTemplate(ServiceTemplate serviceTemplate,
                                                String relationshipId,
                                                TranslationContext context) {
    Map<String, RelationshipTemplate> relationshipTemplates = DataModelUtil
        .getRelationshipTemplates(serviceTemplate);
    if (relationshipTemplates != null) {
      RelationshipTemplate relationshipTemplate = relationshipTemplates.get(relationshipId);
      if (relationshipTemplate != null) {
        String relationshipTemplateType = relationshipTemplate.getType();
        if (relationshipTemplateType.equals(ToscaRelationshipType.CINDER_VOLUME_ATTACHES_TO)) {
          handleCinderVolumeAttachmentRelationshipTemplate(serviceTemplate,
              relationshipTemplate, context);
        }
      }
    }
  }


  private void handleCinderVolumeAttachmentRelationshipTemplate(ServiceTemplate
                                                                    substitutionServiceTemplate,
                                                                RelationshipTemplate
                                                                    relationshipTemplate,
                                                                TranslationContext context) {
    Map<String, Object> properties = relationshipTemplate.getProperties();
    properties.computeIfPresent(HeatConstants.INSTANCE_UUID_PROPERTY_NAME, (key, value) ->
        context.getUnifiedAbstractNodeTemplateId(substitutionServiceTemplate,
            (String) value));
  }

  private String updateIdenticalProperty(NodeTemplate nodeTemplate, String propertyId) {
    Map<String, String> propertyVal = new HashMap<>();
    String inputParamId = IDENTICAL_VALUE_PROPERTY_PREFIX + propertyId
        + IDENTICAL_VALUE_PROPERTY_SUFFIX;
    propertyVal.put(ToscaFunctions.GET_INPUT.getDisplayName(), inputParamId);
    nodeTemplate.getProperties().put(propertyId, propertyVal);
    return inputParamId;
  }

  private void addInputParameter(String parameterId, String parameterType, EntrySchema entrySchema,
                                 ServiceTemplate serviceTemplate) {

    ParameterDefinition parameterDefinition =
        DataModelUtil.createParameterDefinition(parameterType, null, null,
            true, null, null, entrySchema, null);
    DataModelUtil
        .addInputParameterToTopologyTemplate(serviceTemplate, parameterId, parameterDefinition);
  }

  // Return the input parameter Id which is used in the new property value if there is one
  private Optional<String> updateProperty(
      ServiceTemplate serviceTemplate,
      String nodeTemplateId, NodeTemplate nodeTemplate,
      Map.Entry<String, Object> propertyEntry,
      UnifiedCompositionEntity compositionEntity,
      ComputeTemplateConsolidationData computeTemplateConsolidationData,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context) {

    if (handleGetAttrFromConsolidationNodes(serviceTemplate, nodeTemplate, propertyEntry,
        unifiedCompositionDataList, context)) {
      return Optional.empty();
    }

    Map<String, List<String>> propertyVal = new HashMap<>();
    List<String> getInputFuncParams = new ArrayList<>();
    String inputParamId =
        getParameterId(nodeTemplateId, nodeTemplate, propertyEntry.getKey(), compositionEntity,
            computeTemplateConsolidationData);
    getInputFuncParams.add(inputParamId);
    getInputFuncParams.add(ToscaConstants.INDEX_VALUE_PROPERTY_NAME);
    propertyVal.put(ToscaFunctions.GET_INPUT.getDisplayName(), getInputFuncParams);
    nodeTemplate.getProperties().put(propertyEntry.getKey(), propertyVal);
    return Optional.of(inputParamId);
  }

  private boolean handleGetAttrFromConsolidationNodes(
      ServiceTemplate serviceTemplate,
      NodeTemplate nodeTemplate,
      Map.Entry<String, Object> propertyEntry,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context) {
    Map<String, UnifiedCompositionEntity> consolidationNodeTemplateIdAndType =
        getAllConsolidationNodeTemplateIdAndType(unifiedCompositionDataList);

    Set<String> consolidationNodeTemplateIds = consolidationNodeTemplateIdAndType.keySet();
    boolean includeGetAttrFromConsolidationNodes = false;
    boolean includeGetAttrFromOutsideNodes = false;
    List<List<Object>> getAttrFunctionList = extractGetAttrFunction(propertyEntry.getValue());
    for (List<Object> getAttrFunc : getAttrFunctionList) {
      if (consolidationNodeTemplateIds.contains(getAttrFunc.get(0))) {
        includeGetAttrFromConsolidationNodes = true;
      } else {
        includeGetAttrFromOutsideNodes = true;
      }
    }
    if ((includeGetAttrFromConsolidationNodes && includeGetAttrFromOutsideNodes)
        ||
        (includeGetAttrFromConsolidationNodes && isIncludeGetInputFunc(propertyEntry.getValue()))) {
      //This case is currently not supported - this property will be ignored
      return true;
    } else if (includeGetAttrFromConsolidationNodes) {
      Object clonedPropertyValue = getClonedPropertyValue(propertyEntry);
      List<List<Object>> clonedGetAttrFuncList = extractGetAttrFunction(clonedPropertyValue);
      for (List<Object> getAttrFunc : clonedGetAttrFuncList) {
        String targetNodeTemplateId = (String) getAttrFunc.get(0);
        if (consolidationNodeTemplateIds.contains(targetNodeTemplateId)) {
          updatePropertyGetAttrFunc(serviceTemplate, unifiedCompositionDataList, context,
              consolidationNodeTemplateIdAndType, targetNodeTemplateId, getAttrFunc);
        }
      }
      nodeTemplate.getProperties().put(propertyEntry.getKey(), clonedPropertyValue);
      return true;
    }
    return false;
  }

  private void updatePropertyGetAttrFunc(
      ServiceTemplate serviceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      TranslationContext context,
      Map<String, UnifiedCompositionEntity> consolidationNodeTemplateIdAndType,
      String targetNodeTemplateId,
      List<Object> getAttrFunc) {
    UnifiedCompositionEntity targetCompositionEntity =
        consolidationNodeTemplateIdAndType.get(targetNodeTemplateId);
    String targetNewNodeTemplateId =
        getNewNodeTemplateId(serviceTemplate, unifiedCompositionDataList, targetNodeTemplateId,
            targetCompositionEntity);
    getAttrFunc.set(0, targetNewNodeTemplateId);
  }

  private String getNewNodeTemplateId(ServiceTemplate serviceTemplate,
                                      List<UnifiedCompositionData> unifiedCompositionDataList,
                                      String nodeTemplateId,
                                      UnifiedCompositionEntity compositionEntity) {
    switch (compositionEntity) {
      case Compute:
        return getNewComputeNodeTemplateId(serviceTemplate, nodeTemplateId);
      case Port:
        ComputeTemplateConsolidationData connectedComputeConsolidationData =
            getConnectedComputeConsolidationData(serviceTemplate,
                unifiedCompositionDataList, nodeTemplateId);
        NodeTemplate connectedComputeNodeTemplate =
            DataModelUtil.getNodeTemplate(serviceTemplate,
                connectedComputeConsolidationData.getNodeTemplateId());
        return getNewPortNodeTemplateId(nodeTemplateId, connectedComputeNodeTemplate.getType(),
            connectedComputeConsolidationData);
      default:
        return null;
    }
  }

  private String getNewNodeTemplateId(String origNodeTemplateId,
                                      String serviceTemplateFileName,
                                      ServiceTemplate serviceTemplate,
                                      TranslationContext context) {
    ConsolidationData consolidationData = context.getConsolidationData();

    if (isIdIsOfExpectedType(origNodeTemplateId, UnifiedCompositionEntity.Port,
        serviceTemplateFileName,
        consolidationData, context)) {
      return handleIdOfPort(origNodeTemplateId, serviceTemplateFileName, consolidationData);
    } else if (isIdIsOfExpectedType(origNodeTemplateId, UnifiedCompositionEntity.Compute,
        serviceTemplateFileName, consolidationData, context)) {
      NodeTemplate nodeTemplate =
          getComputeNodeTemplate(origNodeTemplateId, serviceTemplate, context);
      return getComputeTypeSuffix(nodeTemplate.getType());
    }

    return null;
  }

  private ComputeTemplateConsolidationData getConnectedComputeConsolidationData(
      ServiceTemplate serviceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList,
      String portNodeTemplateId) {
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      Collection<List<String>> portsCollection =
          unifiedCompositionData.getComputeTemplateConsolidationData().getPorts().values();
      for (List<String> portIdList : portsCollection) {
        for (String portId : portIdList) {
          if (portId.equals(portNodeTemplateId)) {
            return unifiedCompositionData.getComputeTemplateConsolidationData();
          }
        }
      }
    }
    return null;
  }

  private Object getClonedPropertyValue(Map.Entry<String, Object> propertyEntry) {
    if (propertyEntry.getValue() instanceof Map) {
      return getClonedObject(propertyEntry.getValue(), Map.class);
    } else if (propertyEntry.getValue() instanceof List) {
      return getClonedObject(propertyEntry.getValue(), List.class);
    }
    return propertyEntry.getValue();
  }


  private String getParameterId(String nodeTemplateId, NodeTemplate nodeTemplate, String propertyId,
                                UnifiedCompositionEntity unifiedCompositionEntity,
                                ComputeTemplateConsolidationData computeTemplateConsolidationData) {
    switch (unifiedCompositionEntity) {
      case Compute:
        return UnifiedCompositionEntity.Compute.name().toLowerCase() + "_"
            + getComputeTypeSuffix(nodeTemplate.getType()) + "_" + propertyId;
      case Port:
        String portType = ConsolidationDataUtil.getPortType(nodeTemplateId);
        if (computeTemplateConsolidationData.getPorts().get(portType).size() > 1) {
          return UnifiedCompositionEntity.Port.name().toLowerCase() + "_" + nodeTemplateId + "_"
              + propertyId;
        }
        return UnifiedCompositionEntity.Port.name().toLowerCase() + "_" + portType + "_"
            + propertyId;
      default:
        return propertyId;
    }
  }

  private void removeConnectivityOut(EntityConsolidationData entityConsolidationData,
                                     NodeTemplate nodeTemplate) {
    if (MapUtils.isEmpty(entityConsolidationData.getNodesConnectedOut())) {
      return;
    }

    for (List<RequirementAssignmentData> requirementAssignmentDataList : entityConsolidationData
        .getNodesConnectedOut().values()) {
      for (RequirementAssignmentData requirementAssignmentData : requirementAssignmentDataList) {
        DataModelUtil.removeRequirementsAssignment(nodeTemplate.getRequirements(),
            requirementAssignmentData.getRequirementId());
      }
      if (nodeTemplate.getRequirements().isEmpty()) {
        nodeTemplate.setRequirements(null);
      }
    }
  }

  private void removeVolumeConnectivity(
      ComputeTemplateConsolidationData computeTemplateConsolidationData,
      NodeTemplate computeNodeTemplate) {
    if (MapUtils.isEmpty(computeTemplateConsolidationData.getVolumes())) {
      return;
    }
    Collection<List<RequirementAssignmentData>> volumeCollection =
        computeTemplateConsolidationData.getVolumes().values();
    for (List<RequirementAssignmentData> requirementAssignmentDataList : volumeCollection) {
      for (RequirementAssignmentData requirementAssignmentData : requirementAssignmentDataList) {
        DataModelUtil.removeRequirementsAssignment(computeNodeTemplate.getRequirements(),
            requirementAssignmentData.getRequirementId());
      }
    }
    if (computeNodeTemplate.getRequirements().isEmpty()) {
      computeNodeTemplate.setRequirements(null);
    }
  }

  private void createIndexInputParameter(ServiceTemplate substitutionServiceTemplate) {
    ParameterDefinition indexParameterDefinition =
        DataModelUtil.createParameterDefinition(PropertyType.INTEGER.getDisplayName(),
            "Index value of this substitution service template runtime instance", null,
            false, createIndexValueConstraint(), null, null, 0);
    DataModelUtil.addInputParameterToTopologyTemplate(substitutionServiceTemplate,
        ToscaConstants.INDEX_VALUE_PROPERTY_NAME, indexParameterDefinition);
  }


  private List<Constraint> createIndexValueConstraint() {
    List<Constraint> constraints;
    constraints = new ArrayList<>();
    Constraint constraint = new Constraint();
    constraint.setGreater_or_equal(0);
    constraints.add(constraint);
    return constraints;
  }

  private Optional<UnifiedComposition> getUnifiedCompositionInstance(UnifiedCompositionMode mode) {
    String unifiedCompositionImplClassName =
        unifiedCompositionImplMap.get(mode.name()).getImplementationClass();
    if (StringUtils.isEmpty(unifiedCompositionImplClassName)) {
      return Optional.empty();
    }
    return Optional
        .of(CommonMethods.newInstance(unifiedCompositionImplClassName, UnifiedComposition.class));
  }

  private Optional<Map<String, Object>> createAbstractSubstitutionProperties(
      ServiceTemplate serviceTemplate,
      ServiceTemplate substitutionServiceTemplate,
      List<UnifiedCompositionData> unifiedCompositionDataList) {
    Map<String, Object> abstractSubstituteProperties = new LinkedHashMap<>();
    Map<String, ParameterDefinition> substitutionTemplateInputs = DataModelUtil
        .getInputParameters(substitutionServiceTemplate);
    if (substitutionTemplateInputs == null) {
      return Optional.empty();
    }
    //Since all the computes have the same type fetching the type from the first entry
    NodeTemplate firstComputeNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
        unifiedCompositionDataList.get(0)
            .getComputeTemplateConsolidationData().getNodeTemplateId());
    String computeType = getComputeTypeSuffix(firstComputeNodeTemplate.getType());
    for (Map.Entry<String, ParameterDefinition> input : substitutionTemplateInputs.entrySet()) {
      String substitutionTemplateInputName = input.getKey();
      ParameterDefinition inputParameterDefinition = input.getValue();
      String inputType = inputParameterDefinition.getType();
      if (!inputType.equalsIgnoreCase(PropertyType.LIST.getDisplayName())) {
        if (isIdenticalValueProperty(substitutionTemplateInputName)) {
          //Handle identical value properties
          Object abstractPropertyValue = getPropertyValueFromNodeTemplate(
              getIdenticalValuePropertyName(substitutionTemplateInputName),
              firstComputeNodeTemplate);
          abstractSubstituteProperties.put(substitutionTemplateInputName, abstractPropertyValue);
        }
        continue;
      }

      //Check if the input is of type compute or port
      UnifiedCompositionEntity inputUnifiedCompositionEntity = getInputCompositionEntity(
          substitutionTemplateInputName);
      List<Object> abstractPropertyValue = new ArrayList<>();
      Object propertyValue = null;
      switch (inputUnifiedCompositionEntity) {
        case Compute:
          for (UnifiedCompositionData compositionData : unifiedCompositionDataList) {
            ComputeTemplateConsolidationData computeTemplateConsolidationData =
                compositionData.getComputeTemplateConsolidationData();
            propertyValue = getComputePropertyValue(substitutionTemplateInputName,
                serviceTemplate, computeTemplateConsolidationData);
            if (!(propertyValue instanceof Optional)) {
              abstractPropertyValue.add(propertyValue);
            }
          }
          break;
        case Port:
          for (UnifiedCompositionData compositionData : unifiedCompositionDataList) {
            ComputeTemplateConsolidationData computeTemplateConsolidationData = compositionData
                .getComputeTemplateConsolidationData();
            List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
                getPortTemplateConsolidationDataList(compositionData);
            //Get the input type for this input whether it is of type
            // port_<port_node_template_id>_<property_name> or port_<port_type>_<property_name>
            PortInputType portInputType = getPortInputType(substitutionTemplateInputName,
                compositionData);
            for (PortTemplateConsolidationData portTemplateConsolidationData :
                portTemplateConsolidationDataList) {
              //Get the port property value
              String portNodeTemplateId = portTemplateConsolidationData.getNodeTemplateId();
              propertyValue = getPortPropertyValue(substitutionTemplateInputName,
                  computeType, portInputType, serviceTemplate, computeTemplateConsolidationData,
                  portNodeTemplateId);
              //If the value object is Optional.empty it implies that the property name was not
              // found in the input name
              if (!(propertyValue instanceof Optional)) {
                abstractPropertyValue.add(propertyValue);
              }
            }
          }
          break;
        default:
          break;
      }
      abstractSubstituteProperties.put(substitutionTemplateInputName, abstractPropertyValue);
    }
    return Optional.ofNullable(abstractSubstituteProperties);
  }

  private PortInputType getPortInputType(String inputName,
                                         UnifiedCompositionData unifiedCompositionData) {
    String portInputPrefix = UnifiedCompositionEntity.Port.name().toLowerCase() + "_";
    ComputeTemplateConsolidationData computeTemplateConsolidationData = unifiedCompositionData
        .getComputeTemplateConsolidationData();
    List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
        getPortTemplateConsolidationDataList(unifiedCompositionData);
    //Scan the available port node template ids to check if the input is of the form
    // "port_<port_node_template_id>_<property_name>"
    for (PortTemplateConsolidationData portTemplateConsolidationData :
        portTemplateConsolidationDataList) {
      String portNodeTemplateId = portTemplateConsolidationData.getNodeTemplateId();
      String portNodeTemplateIdPrefix = portInputPrefix + portNodeTemplateId;
      if (inputName.startsWith(portNodeTemplateIdPrefix)) {
        return PortInputType.NodeTemplateId;
      }
    }
    //Check whether the input is of the form "port_<port_type>_<property_name>"
    Set<String> portTypes = computeTemplateConsolidationData.getPorts().keySet();
    for (String portType : portTypes) {
      String expectedPortTypeSusbtring = portInputPrefix + portType + "_";
      if (inputName.startsWith(expectedPortTypeSusbtring)) {
        return PortInputType.PortType;
      }
    }
    return PortInputType.Other;
  }

  private void cleanServiceTemplate(ServiceTemplate serviceTemplate,
                                    EntityConsolidationData entity,
                                    TranslationContext context) {
    removeNodeTemplateFromServiceTemplate(serviceTemplate, entity, context);
    updateHeatStackGroup(serviceTemplate, entity, context);
  }

  private void removeNodeTemplateFromServiceTemplate(ServiceTemplate serviceTemplate,
                                                     EntityConsolidationData entity,
                                                     TranslationContext context) {
    String nodeTemplateIdToRemove = entity.getNodeTemplateId();
    Map<String, NodeTemplate> nodeTemplates =
        serviceTemplate.getTopology_template().getNode_templates();
    NodeTemplate nodeTemplateToRemove =
        nodeTemplates.get(nodeTemplateIdToRemove);
    nodeTemplates.remove(nodeTemplateIdToRemove);

    context.addCleanedNodeTemplate(ToscaUtil.getServiceTemplateFileName(serviceTemplate),
        nodeTemplateIdToRemove,
        entity.getClass() == ComputeTemplateConsolidationData.class
            ? UnifiedCompositionEntity.Compute
            : UnifiedCompositionEntity.Port,
        nodeTemplateToRemove);

  }

  private void removeCleanedNodeType(String cleanedNodeTemplateId,
                                     ServiceTemplate serviceTemplate,
                                     TranslationContext context) {
    NodeTemplate cleanedNodeTemplate =
        context
            .getCleanedNodeTemplate(ToscaUtil.getServiceTemplateFileName(serviceTemplate),
                cleanedNodeTemplateId);
    String typeToRemove = cleanedNodeTemplate.getType();

    if (Objects.nonNull(typeToRemove)
        && serviceTemplate.getNode_types().containsKey(typeToRemove)) {
      serviceTemplate.getNode_types().remove(typeToRemove);
    }
  }

  private void updateHeatStackGroup(ServiceTemplate serviceTemplate,
                                    EntityConsolidationData entity,
                                    TranslationContext context) {
    Map<String, GroupDefinition> groups = serviceTemplate.getTopology_template()
        .getGroups() == null ? new HashMap<>()
        : serviceTemplate.getTopology_template().getGroups();
    String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
    String nodeRelatedAbstractNodeId =
        context.getUnifiedAbstractNodeTemplateId(serviceTemplate, entity.getNodeTemplateId());

    for (Map.Entry<String, GroupDefinition> groupEntry : groups.entrySet()) {
      GroupDefinition groupDefinition = groupEntry.getValue();
      if (isHeatStackGroup(groupDefinition.getType())) {
        updateGroupMembersWithNewUnifiedNodeTemplateId(entity, nodeRelatedAbstractNodeId,
            groupEntry);
      }
    }
  }

  private void updateGroupMembersWithNewUnifiedNodeTemplateId(
      EntityConsolidationData entity,
      String newNodetemplateId,
      Map.Entry<String, GroupDefinition> groupEntry) {
    List<String> members = groupEntry.getValue().getMembers();
    if (members.contains(entity.getNodeTemplateId())) {
      members.remove(entity.getNodeTemplateId());
      if (!members.contains(newNodetemplateId)) {
        members.add(newNodetemplateId);
      }
    }
    groupEntry.getValue().setMembers(members);
  }

  private void updateHeatStackGroupNestedComposition(ServiceTemplate serviceTemplate,
                                                     EntityConsolidationData entity,
                                                     TranslationContext context) {
    Map<String, GroupDefinition> groups = serviceTemplate.getTopology_template()
        .getGroups() == null ? new HashMap<>() : serviceTemplate.getTopology_template().getGroups();
    String serviceTemplateFileName = ToscaUtil.getServiceTemplateFileName(serviceTemplate);
    Optional<String> nestedNodeTemplateId =
        context.getUnifiedNestedNodeTemplateId(serviceTemplateFileName, entity.getNodeTemplateId());

    if (nestedNodeTemplateId.isPresent()) {
      for (Map.Entry<String, GroupDefinition> groupEntry : groups.entrySet()) {
        GroupDefinition groupDefinition = groupEntry.getValue();
        if (isHeatStackGroup(groupDefinition.getType())) {
          updateGroupMembersWithNewUnifiedNodeTemplateId(entity, nestedNodeTemplateId.get(),
              groupEntry);
        }
      }
    }
  }

  private void handleNestedNodeTemplateInMainServiceTemplate(String nestedNodeTemplateId,
                                                             ServiceTemplate mainServiceTemplate,
                                                             ServiceTemplate nestedServiceTemplate,
                                                             TranslationContext context) {
    NodeTemplate nestedNodeTemplate = DataModelUtil.getNodeTemplate(mainServiceTemplate,
        nestedNodeTemplateId);
    if (Objects.isNull(nestedNodeTemplate)) {
      return;
    }

    Optional<String> unifiedNestedNodeTypeId = context
        .getUnifiedNestedNodeTypeId(Constants.GLOBAL_SUBSTITUTION_TYPES_TEMPLATE_NAME,
            nestedNodeTemplate.getType());
    unifiedNestedNodeTypeId
        .ifPresent(unifiedNestedNodeTypeIdVal -> updateNestedNodeTemplate(
            unifiedNestedNodeTypeIdVal, nestedNodeTemplateId, nestedNodeTemplate,
            mainServiceTemplate, nestedServiceTemplate, context));
  }

  private void handleSubstitutionMappingInNestedServiceTemplate(
      String newNestedNodeType,
      ServiceTemplate nestedServiceTemplate) {
    if (Objects.isNull(newNestedNodeType)) {
      return;
    }

    SubstitutionMapping substitutionMappings =
        nestedServiceTemplate.getTopology_template().getSubstitution_mappings();
    substitutionMappings.setNode_type(newNestedNodeType);
  }

  private void updateNestedNodeTemplate(String newNestedNodeTypeId,
                                        String nestedNodeTemplateId,
                                        NodeTemplate nestedNodeTemplate,
                                        ServiceTemplate mainServiceTemplate,
                                        ServiceTemplate nestedServiceTemplate,
                                        TranslationContext context) {
    String mainServiceTemplateName = ToscaUtil.getServiceTemplateFileName(mainServiceTemplate);
    int index =
        context.getHandledNestedComputeNodeTemplateIndex(mainServiceTemplateName,
            newNestedNodeTypeId);
    String newNodeTemplateId =
        Constants.ABSTRACT_NODE_TEMPLATE_ID_PREFIX + getComputeTypeSuffix(newNestedNodeTypeId) + "_" + index;

    nestedNodeTemplate.setType(newNestedNodeTypeId);
    mainServiceTemplate.getTopology_template().getNode_templates().remove(nestedNodeTemplateId);
    mainServiceTemplate.getTopology_template().getNode_templates()
        .put(newNodeTemplateId, nestedNodeTemplate);

    context
        .addUnifiedNestedNodeTemplateId(mainServiceTemplateName,
            nestedNodeTemplateId, newNodeTemplateId);
  }

  private void handleNestedNodeTypesInGlobalSubstituteServiceTemplate(
      String origNestedNodeTypeId,
      String newNestedNodeTypeId,
      ServiceTemplate globalSubstitutionServiceTemplate,
      TranslationContext context) {
    Map<String, NodeType> nodeTypes = globalSubstitutionServiceTemplate.getNode_types();
    NodeType nested = DataModelUtil.getNodeType(globalSubstitutionServiceTemplate,
        origNestedNodeTypeId);
    setNewValuesForNestedNodeType(origNestedNodeTypeId, newNestedNodeTypeId, nested, nodeTypes);
    context.addUnifiedNestedNodeTypeId(Constants.GLOBAL_SUBSTITUTION_TYPES_TEMPLATE_NAME,
        origNestedNodeTypeId, newNestedNodeTypeId);
  }

  private void setNewValuesForNestedNodeType(String origNestedNodeType,
                                             String newNestedNodeTypeId,
                                             NodeType nested,
                                             Map<String, NodeType> nodeTypes) {
    if (Objects.nonNull(nested)) {
      nested.setDerived_from(ToscaNodeType.VFC_ABSTRACT_SUBSTITUTE);
      nodeTypes.remove(origNestedNodeType);
      nodeTypes.put(newNestedNodeTypeId, nested);
    }
  }

  private Optional<String> getNewNestedNodeTypeId(ServiceTemplate mainServiceTemplate,
                                                  ServiceTemplate nestedServiceTemplate,
                                                  TranslationContext context) {
    FileComputeConsolidationData fileComputeConsolidationData =
        context.getConsolidationData().getComputeConsolidationData()
            .getFileComputeConsolidationData(
                ToscaUtil.getServiceTemplateFileName(nestedServiceTemplate));

    if (Objects.nonNull(fileComputeConsolidationData)) {
      String nestedNodeTypePrefix = ToscaNodeType.ABSTRACT_NODE_TYPE_PREFIX + "heat.";
      return Optional
          .of(nestedNodeTypePrefix + getComputeTypeInNestedFile(fileComputeConsolidationData));
    }
    return Optional.empty();
  }

  private String getComputeTypeInNestedFile(
      FileComputeConsolidationData fileComputeConsolidationData) {
    List<TypeComputeConsolidationData> typeComputeConsolidationDatas =
        new ArrayList<>(fileComputeConsolidationData.getAllTypeComputeConsolidationData());
    if (typeComputeConsolidationDatas.size() == 0) {
      return null;
    } else {
      String computeNodeType = fileComputeConsolidationData.getAllComputeTypes().iterator().next();
      return getComputeTypeSuffix(computeNodeType);
    }
  }

  private void handleGetAttrInAbstractNodeTemplate(ServiceTemplate serviceTemplate,
                                                   TranslationContext context,
                                                   String serviceTemplateFileName,
                                                   NodeTemplate abstractNodeTemplate) {
    Map<String, Object> properties =
        abstractNodeTemplate == null || abstractNodeTemplate.getProperties() == null
            ? new HashMap<>()
            : abstractNodeTemplate.getProperties();
    for (Object propertyValue : properties.values()) {
      List<List<Object>> getAttrList = extractGetAttrFunction(propertyValue);
      for (List<Object> getAttrFuncValue : getAttrList) {
        String origNodeTemplateId = (String) getAttrFuncValue.get(0);
        Optional<String> nestedNodeTemplateId = context.getUnifiedNestedNodeTemplateId(ToscaUtil
            .getServiceTemplateFileName(serviceTemplate), origNodeTemplateId);
        if (nestedNodeTemplateId.isPresent()) {
          getAttrFuncValue.set(0, nestedNodeTemplateId.get());
        } else {
          replaceGetAttrNodeIdAndAttrName(serviceTemplate, context, serviceTemplateFileName,
              getAttrFuncValue);
        }
      }
    }
  }

  private void replaceGetAttrNodeIdAndAttrName(ServiceTemplate serviceTemplate,
                                               TranslationContext context,
                                               String serviceTemplateFileName,
                                               List<Object> getAttrFuncValue) {
    String origNodeTemplateId = (String) getAttrFuncValue.get(0);
    String attributeName = (String) getAttrFuncValue.get(1);

    String unifiedAbstractNodeTemplateId =
        context.getUnifiedAbstractNodeTemplateId(serviceTemplate, origNodeTemplateId);

    if (Objects.isNull(unifiedAbstractNodeTemplateId)) {
      return;
    }

    String newNodeTemplateId =
        getNewNodeTemplateId(origNodeTemplateId, serviceTemplateFileName, serviceTemplate, context);

    String newSubstitutionOutputParameterId =
        getNewSubstitutionOutputParameterId(newNodeTemplateId, attributeName);

    getAttrFuncValue.set(0, unifiedAbstractNodeTemplateId);
    getAttrFuncValue.set(1, newSubstitutionOutputParameterId);
  }

  private NodeTemplate getComputeNodeTemplate(String origNodeTemplateId,
                                              ServiceTemplate serviceTemplate,
                                              TranslationContext context) {
    NodeTemplate computeNodeTemplate =
        DataModelUtil.getNodeTemplate(serviceTemplate, origNodeTemplateId);
    if (computeNodeTemplate == null) {
      computeNodeTemplate =
          context.getCleanedNodeTemplate(ToscaUtil.getServiceTemplateFileName(serviceTemplate),
              origNodeTemplateId);
    }
    return computeNodeTemplate;
  }

  private String handleIdOfPort(String origNodeTemplateId, String serviceTemplateFileName,
                                ConsolidationData consolidationData) {
    Optional<Pair<String, ComputeTemplateConsolidationData>>
        computeTypeAndComputeTemplateByPortId =
        getComputeTypeAndComputeTemplateByPortId(origNodeTemplateId, serviceTemplateFileName,
            consolidationData);
    if (computeTypeAndComputeTemplateByPortId.isPresent()) {
      Pair<String, ComputeTemplateConsolidationData> computeIdToComputeData =
          computeTypeAndComputeTemplateByPortId.get();
      return getNewPortNodeTemplateId(origNodeTemplateId, computeIdToComputeData.getKey(),
          computeIdToComputeData.getValue());
    }

    return null;
  }

  private Optional<Pair<String, ComputeTemplateConsolidationData>>
  getComputeTypeAndComputeTemplateByPortId(String portId, String serviceTemplateFileName,
                                           ConsolidationData consolidationData) {
    FileComputeConsolidationData fileComputeConsolidationData =
        consolidationData.getComputeConsolidationData()
            .getFileComputeConsolidationData(serviceTemplateFileName);
    Set<String> computeTypes =
        fileComputeConsolidationData.getAllComputeTypes();

    for (String computeType : computeTypes) {
      Collection<ComputeTemplateConsolidationData> computeTemplateConsolidationDatas =
          fileComputeConsolidationData.getTypeComputeConsolidationData(computeType)
              .getAllComputeTemplateConsolidationData();

      for (ComputeTemplateConsolidationData compute : computeTemplateConsolidationDatas) {
        if (ConsolidationDataUtil.isComputeReferenceToPortId(compute, portId)) {
          return Optional.of(new ImmutablePair<>(computeType, compute));
        }
      }
    }

    return Optional.empty();
  }

  private boolean isIdIsOfExpectedType(String id,
                                       UnifiedCompositionEntity expectedUnifiedCompositionEntity,
                                       String serviceTemplateFileName,
                                       ConsolidationData consolidationData,
                                       TranslationContext context) {
    UnifiedSubstitutionData unifiedSubstitutionData =
        context.getUnifiedSubstitutionData().get(serviceTemplateFileName);
    if (Objects.isNull(unifiedSubstitutionData)) {
      return false;
    }

    UnifiedCompositionEntity actualUnifiedCompositionEntity =
        unifiedSubstitutionData.getCleanedNodeTemplateCompositionEntity(id);

    return actualUnifiedCompositionEntity == null ? false
        : actualUnifiedCompositionEntity.equals(expectedUnifiedCompositionEntity);
  }

  private boolean isHeatStackGroup(String groupType) {
    return groupType.equals(ToscaGroupType.HEAT_STACK);
  }

  private Object getPortPropertyValue(String inputName,
                                      String computeType,
                                      PortInputType portInputType,
                                      ServiceTemplate serviceTemplate,
                                      ComputeTemplateConsolidationData
                                          computeTemplateConsolidationData,
                                      String portNodeTemplateId) {
    //Get the input prefix to extract the property name from the input name
    String portInputPrefix = getPortInputPrefix(computeTemplateConsolidationData,
        portNodeTemplateId, portInputType);
    //Get the property name from the input
    Optional<String> propertyName = getPropertyNameFromInput(inputName,
        UnifiedCompositionEntity.Port, computeType, portInputPrefix);
    //Get the property value from the node template
    if (propertyName.isPresent()) {
      NodeTemplate portNodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
          portNodeTemplateId);
      return getPropertyValueFromNodeTemplate(propertyName.get(), portNodeTemplate);
    }
    return Optional.empty();
  }

  private Optional<String> getPortTypeFromInput(
      String inputName,
      String portNodeTemplateId,
      ComputeTemplateConsolidationData computeTemplateConsolidationData) {
    String portTypeFromInput = null;
    String portInputPrefix = UnifiedCompositionEntity.Port.name().toLowerCase() + "_";
    String portNodeTemplateIdPrefix = portInputPrefix + portNodeTemplateId;
    if (inputName.startsWith(portNodeTemplateIdPrefix)) {
      return Optional.empty();
    }
    Set<String> portTypes = computeTemplateConsolidationData.getPorts().keySet();
    for (String portType : portTypes) {
      String expectedPortTypeSusbtring = "_" + portType + "_";
      if (inputName.contains(expectedPortTypeSusbtring)) {
        portTypeFromInput = portType;
        break;
      }
    }
    return Optional.ofNullable(portTypeFromInput);
  }

  private Object getComputePropertyValue(
      String inputName,
      ServiceTemplate serviceTemplate,
      ComputeTemplateConsolidationData computeTemplateConsolidationData) {
    NodeTemplate nodeTemplate = DataModelUtil.getNodeTemplate(serviceTemplate,
        computeTemplateConsolidationData.getNodeTemplateId());
    String nodeType = getComputeTypeSuffix(nodeTemplate.getType());
    Optional<String> propertyName =
        getPropertyNameFromInput(inputName, UnifiedCompositionEntity.Compute, nodeType, null);
    if (propertyName.isPresent()) {
      return getPropertyValueFromNodeTemplate(propertyName.get(), nodeTemplate);
    }
    return Optional.empty();
  }

  private UnifiedCompositionEntity getInputCompositionEntity(String inputName) {
    UnifiedCompositionEntity inputCompositionEntity = UnifiedCompositionEntity.Other;
    String inputType = inputName.substring(0, inputName.indexOf('_'));
    if (inputType.equals(UnifiedCompositionEntity.Compute.name().toLowerCase())) {
      inputCompositionEntity = UnifiedCompositionEntity.Compute;
    } else if (inputType.equals(UnifiedCompositionEntity.Port.name().toLowerCase())) {
      inputCompositionEntity = UnifiedCompositionEntity.Port;
    }
    return inputCompositionEntity;
  }

  private Optional<String> getPropertyNameFromInput(
      String inputName,
      UnifiedCompositionEntity compositionEntity,
      String computeType, String portInputPrefix) {
    String propertyName = null;
    switch (compositionEntity) {
      case Compute:
        propertyName = inputName.substring(inputName.lastIndexOf(computeType)
            + computeType.length() + 1);
        break;
      case Port:
        if (inputName.startsWith(portInputPrefix)) {
          propertyName = inputName.split(portInputPrefix)[1];
        }
        break;
      default:
        break;
    }
    return Optional.ofNullable(propertyName);
  }

  private String getPortInputPrefix(
      ComputeTemplateConsolidationData computeTemplateConsolidationData,
      String portNodeTemplateId,
      PortInputType portInputType) {
    String portInputPrefix = UnifiedCompositionEntity.Port.name().toLowerCase() + "_";
    String portType = ConsolidationDataUtil.getPortType(portNodeTemplateId);
    if (portInputType == PortInputType.NodeTemplateId) {
      portInputPrefix += portNodeTemplateId + "_";
    } else if (portInputType == PortInputType.PortType) {
      portInputPrefix += portType + "_";
    }
    return portInputPrefix;
  }

  private boolean isIdenticalValueProperty(String inputName) {
    StringBuilder builder = new StringBuilder(IDENTICAL_VALUE_PROPERTY_PREFIX);
    builder.append("[a-z]+");
    builder.append(IDENTICAL_VALUE_PROPERTY_SUFFIX);
    boolean isMatchingProperty = Pattern.matches(builder.toString(), inputName);
    List<String> identicalValuePropertyList = consolidationService.getPropertiesWithIdenticalVal();
    if (isMatchingProperty
        && identicalValuePropertyList.contains(getIdenticalValuePropertyName(inputName))) {
      return true;
    }
    return false;
  }

  private String getIdenticalValuePropertyName(String input) {
    return input.split("_")[1];
  }

  private Object getPropertyValueFromNodeTemplate(String propertyName, NodeTemplate nodeTemplate) {
    Map<String, Object> nodeTemplateProperties = nodeTemplate.getProperties();
    if (nodeTemplateProperties != null) {
      Object propertyValue = nodeTemplateProperties.get(propertyName);
      propertyValue = getClonedObject(propertyValue);
      return propertyValue;
    }
    return null;
  }

  private <T> Object getClonedObject(Object objectValue, Class<T> clazz) {
    YamlUtil yamlUtil = new YamlUtil();
    Object clonedObjectValue;
    String objectToYaml = yamlUtil.objectToYaml(objectValue);
    clonedObjectValue = yamlUtil.yamlToObject(objectToYaml, clazz);
    return clonedObjectValue;
  }

  private Object getClonedObject(Object objectValue) {
    Object clonedObjectValue;
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
      objectOutputStream.writeObject(objectValue);

      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream
          .toByteArray());
      ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
      clonedObjectValue = objectInputStream.readObject();
    } catch (NotSerializableException ex) {
      return getClonedObject(objectValue, objectValue.getClass());
    } catch (IOException ioe) {
      return null;
    } catch (ClassNotFoundException cnfe) {
      return null;
    }
    return clonedObjectValue;
  }

  private Map<String, UnifiedCompositionEntity> getAllConsolidationNodeTemplateIdAndType(
      List<UnifiedCompositionData> unifiedCompositionDataList) {

    Map<String, UnifiedCompositionEntity> consolidationNodeTemplateIdAndType = new HashMap<>();
    for (UnifiedCompositionData unifiedCompositionData : unifiedCompositionDataList) {
      ComputeTemplateConsolidationData computeTemplateConsolidationData =
          unifiedCompositionData.getComputeTemplateConsolidationData();
      if (Objects.nonNull(computeTemplateConsolidationData)) {
        consolidationNodeTemplateIdAndType
            .put(computeTemplateConsolidationData.getNodeTemplateId(),
                UnifiedCompositionEntity.Compute);
      }
      List<PortTemplateConsolidationData> portTemplateConsolidationDataList =
          getPortTemplateConsolidationDataList(unifiedCompositionData);
      for (PortTemplateConsolidationData portTemplateConsolidationData :
          portTemplateConsolidationDataList) {
        consolidationNodeTemplateIdAndType.put(portTemplateConsolidationData.getNodeTemplateId(),
            UnifiedCompositionEntity.Port);
      }
      NestedTemplateConsolidationData nestedTemplateConsolidationData =
          unifiedCompositionData.getNestedTemplateConsolidationData();
      if (Objects.nonNull(nestedTemplateConsolidationData)) {
        consolidationNodeTemplateIdAndType
            .put(nestedTemplateConsolidationData.getNodeTemplateId(),
                UnifiedCompositionEntity.Nested);
      }
    }
    return consolidationNodeTemplateIdAndType;
  }

  private enum PortInputType {
    NodeTemplateId,
    PortType,
    Other;
  }

  private List<PortTemplateConsolidationData> getPortTemplateConsolidationDataList(
      UnifiedCompositionData unifiedCompositionData) {
    return unifiedCompositionData.getPortTemplateConsolidationDataList() == null ? new
        ArrayList<>() : unifiedCompositionData.getPortTemplateConsolidationDataList();
  }
}
