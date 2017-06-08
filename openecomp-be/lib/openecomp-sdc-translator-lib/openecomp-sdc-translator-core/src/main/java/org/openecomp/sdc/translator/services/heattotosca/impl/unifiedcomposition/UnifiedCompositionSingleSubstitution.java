package org.openecomp.sdc.translator.services.heattotosca.impl.unifiedcomposition;

import org.apache.commons.collections4.CollectionUtils;
import org.openecomp.sdc.tosca.datatypes.model.ServiceTemplate;
import org.openecomp.sdc.translator.datatypes.heattotosca.TranslationContext;
import org.openecomp.sdc.translator.datatypes.heattotosca.unifiedmodel.composition.UnifiedCompositionData;
import org.openecomp.sdc.translator.services.heattotosca.UnifiedComposition;
import org.openecomp.sdc.translator.services.heattotosca.UnifiedCompositionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * The type Unified composition single substitution.
 */
public class UnifiedCompositionSingleSubstitution implements UnifiedComposition {

  // There is no consolidation in SingleSubstitution implemetation.
  // In case of single substitution, if there is more than one entry in the
  // unifiedComposotionDataList, they all should contain the same compute type but the
  // consolidation between them was canceled.
  // For different compute type, this implementation will be called more than once, each time
  // per diff compute type, while sending one entry in the unifiedComposotionDataList.
  @Override
  public void createUnifiedComposition(ServiceTemplate serviceTemplate,
                                       ServiceTemplate nestedServiceTemplate,
                                       List<UnifiedCompositionData> unifiedCompositionDataList,
                                       TranslationContext context) {
    UnifiedCompositionService unifiedCompositionService = new UnifiedCompositionService();
    if (CollectionUtils.isEmpty(unifiedCompositionDataList)) {
      return;
    }

    for (int i = 0; i < unifiedCompositionDataList.size(); i++) {
      List<UnifiedCompositionData> singleSubstitutionUnifiedList = new ArrayList<>();
      singleSubstitutionUnifiedList.add(unifiedCompositionDataList.get(i));

      Integer index = unifiedCompositionDataList.size() > 1 ? i : null;
      Optional<ServiceTemplate> substitutionServiceTemplate =
          unifiedCompositionService.createUnifiedSubstitutionServiceTemplate(serviceTemplate,
              singleSubstitutionUnifiedList, context, index);

      if (!substitutionServiceTemplate.isPresent()) {
        continue;
      }

      String abstractNodeTemplateId = unifiedCompositionService
          .createAbstractSubstituteNodeTemplate(serviceTemplate, substitutionServiceTemplate.get(),
              singleSubstitutionUnifiedList, context, index);

      unifiedCompositionService
          .updateCompositionConnectivity(serviceTemplate, singleSubstitutionUnifiedList, context);

      unifiedCompositionService
          .cleanUnifiedCompositionEntities(serviceTemplate, singleSubstitutionUnifiedList, context);

    }

    unifiedCompositionService
        .cleanNodeTypes(serviceTemplate, unifiedCompositionDataList, context);

  }
}
