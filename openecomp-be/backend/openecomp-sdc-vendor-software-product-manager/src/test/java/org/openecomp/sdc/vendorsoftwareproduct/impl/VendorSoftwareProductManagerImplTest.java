/*-
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

package org.openecomp.sdc.vendorsoftwareproduct.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.openecomp.core.enrichment.factory.EnrichmentManagerFactory;
import org.openecomp.core.enrichment.types.ArtifactType;
import org.openecomp.core.factory.impl.AbstractFactoryBase;
import org.openecomp.core.model.dao.EnrichedServiceModelDao;
import org.openecomp.core.model.dao.ServiceModelDao;
import org.openecomp.core.model.types.ServiceElement;
import org.openecomp.core.utilities.file.FileContentHandler;
import org.openecomp.core.utilities.file.FileUtils;
import org.openecomp.sdc.activityLog.ActivityLogManager;
import org.openecomp.sdc.activitylog.dao.type.ActivityLogEntity;
import org.openecomp.sdc.common.errors.CoreException;
import org.openecomp.sdc.common.errors.ErrorCategory;
import org.openecomp.sdc.common.errors.ErrorCode;
import org.openecomp.sdc.common.errors.Messages;
import org.openecomp.sdc.common.errors.ValidationErrorBuilder;
import org.openecomp.sdc.healing.api.HealingManager;
import org.openecomp.sdc.tosca.datatypes.ToscaServiceModel;
import org.openecomp.sdc.tosca.datatypes.model.CapabilityDefinition;
import org.openecomp.sdc.vendorlicense.facade.VendorLicenseFacade;
import org.openecomp.sdc.vendorlicense.licenseartifacts.VendorLicenseArtifactsService;
import org.openecomp.sdc.vendorsoftwareproduct.MibManager;
import org.openecomp.sdc.vendorsoftwareproduct.OrchestrationTemplateCandidateManager;
import org.openecomp.sdc.vendorsoftwareproduct.VendorSoftwareProductConstants;
import org.openecomp.sdc.vendorsoftwareproduct.dao.OrchestrationTemplateDao;
import org.openecomp.sdc.vendorsoftwareproduct.dao.PackageInfoDao;
import org.openecomp.sdc.vendorsoftwareproduct.dao.VendorSoftwareProductDao;
import org.openecomp.sdc.vendorsoftwareproduct.dao.VendorSoftwareProductInfoDao;
import org.openecomp.sdc.vendorsoftwareproduct.dao.type.ComponentEntity;
import org.openecomp.sdc.vendorsoftwareproduct.dao.type.PackageInfo;
import org.openecomp.sdc.vendorsoftwareproduct.dao.type.UploadDataEntity;
import org.openecomp.sdc.vendorsoftwareproduct.dao.type.VspDetails;
import org.openecomp.sdc.vendorsoftwareproduct.errors.VendorSoftwareProductErrorCodes;
import org.openecomp.sdc.vendorsoftwareproduct.impl.mock.EnrichmentManagerFactoryImpl;
import org.openecomp.sdc.vendorsoftwareproduct.informationArtifact.InformationArtifactGenerator;
import org.openecomp.sdc.vendorsoftwareproduct.services.composition.CompositionEntityDataManager;
import org.openecomp.sdc.vendorsoftwareproduct.types.UploadFileResponse;
import org.openecomp.sdc.vendorsoftwareproduct.types.ValidationResponse;
import org.openecomp.sdc.vendorsoftwareproduct.types.VersionedVendorSoftwareProductInfo;
import org.openecomp.sdc.versioning.VersioningManager;
import org.openecomp.sdc.versioning.dao.types.Version;
import org.openecomp.sdc.versioning.dao.types.VersionStatus;
import org.openecomp.sdc.versioning.errors.EditOnEntityLockedByOtherErrorBuilder;
import org.openecomp.sdc.versioning.errors.EntityNotExistErrorBuilder;
import org.openecomp.sdc.versioning.types.VersionInfo;
import org.openecomp.sdc.versioning.types.VersionableEntityAction;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/**
 * Created by TALIO on 4/23/2016
 */
public class VendorSoftwareProductManagerImplTest {
  private static final String INVALID_VERSION_MSG = "Invalid requested version.";

  private static String VSP_ID = "vspId";
  private static String VERSION_ID = "versionId";
  public static final Version VERSION01 = new Version(0, 1);
  private static final Version VERSION10 = new Version(1, 0);
  private static final String USER1 = "vspTestUser1";
  private static final String USER2 = "vspTestUser2";
  private static final String USER3 = "vspTestUser3";
  private static String id006 = null;
  private static String id007 = null;

  @Mock
  private VersioningManager versioningManagerMock;
  @Mock
  private VendorSoftwareProductDao vendorSoftwareProductDaoMock; // todo get rid of
  @Mock
  private OrchestrationTemplateDao orchestrationTemplateDataDaoMock;
  @Mock
  private VendorLicenseFacade vendorLicenseFacadeMock;
  @Mock
  private ServiceModelDao<ToscaServiceModel, ServiceElement> serviceModelDaoMock;
  @Mock
  private EnrichedServiceModelDao<ToscaServiceModel, ServiceElement> enrichedServiceModelDaoMock;
  @Mock
  private HealingManager healingManagerMock;
  @Mock
  private VendorLicenseArtifactsService licenseArtifactsServiceMock;
  @Mock
  private CompositionEntityDataManager compositionEntityDataManagerMock;
  @Mock
  private InformationArtifactGenerator informationArtifactGeneratorMock;
  @Mock
  private ActivityLogManager activityLogManagerMock;
  @Mock
  private PackageInfoDao packageInfoDao;
  @Mock
  private VendorSoftwareProductInfoDao vspInfoDaoMock;


  @Spy
  @InjectMocks
  private VendorSoftwareProductManagerImpl vendorSoftwareProductManager;

  private OrchestrationTemplateCandidateManager candidateManager;
  private MibManager mibManager;

  @Captor
  private ArgumentCaptor<ActivityLogEntity> activityLogEntityArg;

  @BeforeMethod
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testListWhenNone() {
    doReturn(new HashMap<>()).when(versioningManagerMock).listEntitiesVersionInfo
        (VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, USER1,
            VersionableEntityAction.Read);
    List<VersionedVendorSoftwareProductInfo> vsps =
        vendorSoftwareProductManager.listVsps(null, USER1);
    Assert.assertEquals(vsps.size(), 0);
  }

  @Test
  public void testList() {
    String vsp1id = "vsp1_id";
    String vsp2id = "vsp2_id";
    Map<String, VersionInfo> vspsTobeReturned = new HashMap<>();

    VersionInfo versionInfo1 = new VersionInfo();
    versionInfo1.setActiveVersion(VERSION01);
    vspsTobeReturned.put(vsp1id, versionInfo1);

    VersionInfo versionInfo2 = new VersionInfo();
    versionInfo2.setActiveVersion(VERSION10);
    vspsTobeReturned.put(vsp2id, versionInfo2);

    doReturn(vspsTobeReturned).when(versioningManagerMock).listEntitiesVersionInfo
        (VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, USER1,
            VersionableEntityAction.Read);

    VspDetails vsp1 = new VspDetails(vsp1id, VERSION01);
    vsp1.setWritetimeMicroSeconds(8L);
    doReturn(vsp1).when(vspInfoDaoMock)
        .get(any(VspDetails.class));

    List<VersionedVendorSoftwareProductInfo> vsps =
        vendorSoftwareProductManager.listVsps(null, USER1);
    Assert.assertEquals(vsps.size(), 2);
  }

  @Test
  public void testListFinalsWhenNone() {
    String vsp1id = "vsp1_id";
    String vsp2id = "vsp2_id";
    Map<String, VersionInfo> vspsTobeReturned = new HashMap<>();

    VersionInfo versionInfo1 = new VersionInfo();
    versionInfo1.setActiveVersion(VERSION01);
    vspsTobeReturned.put(vsp1id, versionInfo1);

    VersionInfo versionInfo2 = new VersionInfo();
    versionInfo2.setActiveVersion(VERSION10);
    vspsTobeReturned.put(vsp2id, versionInfo2);

    doReturn(vspsTobeReturned).when(versioningManagerMock).listEntitiesVersionInfo
        (VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, USER1,
            VersionableEntityAction.Read);

    List<VersionedVendorSoftwareProductInfo> vsps =
        vendorSoftwareProductManager.listVsps(VersionStatus.Final.name(), USER1);
    Assert.assertEquals(vsps.size(), 0);
  }

  @Test
  public void testListFinals() {
    String vsp1id = "vsp1_id";
    String vsp2id = "vsp2_id";
    Map<String, VersionInfo> vspsTobeReturned = new HashMap<>();

    VersionInfo versionInfo1 = new VersionInfo();
    versionInfo1.setActiveVersion(VERSION01);
    vspsTobeReturned.put(vsp1id, versionInfo1);

    VersionInfo versionInfo2 = new VersionInfo();
    versionInfo2.setActiveVersion(new Version(1, 3));
    versionInfo2.setLatestFinalVersion(VERSION10);
    vspsTobeReturned.put(vsp2id, versionInfo2);

    doReturn(vspsTobeReturned).when(versioningManagerMock).listEntitiesVersionInfo
        (VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, USER1,
            VersionableEntityAction.Read);

    VspDetails vsp2 = new VspDetails(vsp2id, VERSION10);
    vsp2.setWritetimeMicroSeconds(8L);
    doReturn(vsp2).when(vspInfoDaoMock)
        .get(any(VspDetails.class));

    List<VersionedVendorSoftwareProductInfo> vsps =
        vendorSoftwareProductManager.listVsps(VersionStatus.Final.name(), USER1);
    Assert.assertEquals(vsps.size(), 1);
  }

  @Test(expectedExceptions = CoreException.class)
  public void testCreateWithExistingName_negative() {
    doThrow(new CoreException(
        new ErrorCode.ErrorCodeBuilder().withCategory(ErrorCategory.APPLICATION).build()))
        .when(vendorSoftwareProductManager).validateUniqueName("Vsp1");
    VspDetails expectedVsp =
        createVspDetails(null, null, "Vsp1", "Test-vsp", "vendorName", "vlm1Id", "icon",
            "category", "subCategory", "123", null);

    vendorSoftwareProductManager.createVsp(expectedVsp, USER1);
  }

  @Test
  public void testCreate() {
    doNothing().when(vendorSoftwareProductManager).validateUniqueName("Vsp1");
    doNothing().when(vendorSoftwareProductManager).createUniqueName("Vsp1");
    doReturn(VERSION01).when(versioningManagerMock).create(anyObject(), anyObject(), anyObject());
    doReturn("{}")
        .when(vendorSoftwareProductManager).getVspQuestionnaireSchema(anyObject());

    VspDetails vspToCreate =
        createVspDetails(null, null, "Vsp1", "Test-vsp", "vendorName", "vlm1Id", "icon",
            "category", "subCategory", "123", null);

    VspDetails vsp = vendorSoftwareProductManager.createVsp(vspToCreate, USER1);

    Assert.assertNotNull(vsp);
    vspToCreate.setId(vsp.getId());
    vspToCreate.setVersion(VERSION01);
    assertVspsEquals(vsp, vspToCreate);
    verify(activityLogManagerMock).addActionLog(activityLogEntityArg.capture(), eq(USER1));
    ActivityLogEntity activityLogEntity = activityLogEntityArg.getValue();
    Assert.assertEquals(activityLogEntity.getVersionId(), String.valueOf(VERSION01.getMajor()+1));
    Assert.assertTrue(activityLogEntity.isSuccess());
  }

  @Test(expectedExceptions = CoreException.class)
  public void testUpdateWithExistingName_negative() {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION01);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1,
        VersionableEntityAction.Write);

    VspDetails existingVsp =
        createVspDetails(VSP_ID, VERSION01, "Vsp1", "Test-existingVsp", "vendorName", "vlm1Id",
            "icon", "category", "subCategory", "123", null);
    VspDetails updatedVsp =
        createVspDetails(VSP_ID, VERSION01, "Vsp1_updated", "Test-existingVsp", "vendorName",
            "vlm1Id", "icon", "category", "subCategory", "123", null);
    doReturn(existingVsp).when(vspInfoDaoMock)
        .get(any(VspDetails.class));
    doThrow(new CoreException(
        new ErrorCode.ErrorCodeBuilder().withCategory(ErrorCategory.APPLICATION).build()))
        .when(vendorSoftwareProductManager)
        .updateUniqueName(existingVsp.getName(), updatedVsp.getName());

    vendorSoftwareProductManager.updateVsp(updatedVsp, USER1);
  }

  @Test
  public void testUpdate() {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION01);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1,
        VersionableEntityAction.Write);
    VspDetails existingVsp =
        createVspDetails(VSP_ID, VERSION01, "VSP1", null, "vendorName", "vlm1Id", "icon",
            "category",
            "subCategory", "456", null);
    VspDetails updatedVsp =
        createVspDetails(VSP_ID, VERSION01, "VSP1_updated", null, "vendorName", "vlm1Id", "icon",
            "category_updated",
            "subCategory", "456", null);
    existingVsp.setWritetimeMicroSeconds(8L);
    doReturn(existingVsp).when(vspInfoDaoMock)
        .get(any(VspDetails.class));
    doNothing().when(vendorSoftwareProductManager)
        .updateUniqueName(existingVsp.getName(), updatedVsp.getName());

    vendorSoftwareProductManager.updateVsp(updatedVsp, USER1);

    verify(vspInfoDaoMock).update(updatedVsp);
  }

  @Test(expectedExceptions = CoreException.class)
  public void testGetNonExistingVersion_negative() {
    Version notExistversion = new Version(43, 8);
    doReturn(null).when(vspInfoDaoMock).get(any(VspDetails.class));
    vendorSoftwareProductManager.getVsp(VSP_ID, notExistversion, USER1);
  }

  @Test
  public void testGetCheckedOutVersion() {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION01);
    versionInfo.setStatus(VersionStatus.Locked);
    versionInfo.setLockingUser(USER1);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1,
        VersionableEntityAction.Read);

    VspDetails existingVsp =
        createVspDetails(VSP_ID, VERSION01, "VSP1", null, "vendorName", "vlm1Id", "icon",
            "category",
            "subCategory", "456", null);
    existingVsp.setWritetimeMicroSeconds(8L);
    doReturn(existingVsp).when(vspInfoDaoMock).get(any(VspDetails.class));

    VspDetails actualVsp =
        vendorSoftwareProductManager.getVsp(VSP_ID, VERSION01, USER1);

    assertVspsEquals(actualVsp, existingVsp);
  }

  @Test
  public void testGetOldVersion() {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(new Version(0, 2));
    versionInfo.setViewableVersions(Arrays.asList(VERSION01, new Version(0, 2)));
    versionInfo.setStatus(VersionStatus.Locked);
    versionInfo.setLockingUser(USER2);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1,
        VersionableEntityAction.Read);

    VspDetails existingVsp =
        createVspDetails(VSP_ID, VERSION01, "VSP1", null, "vendorName", "vlm1Id", "icon",
            "category",
            "subCategory", "456", null);
    existingVsp.setWritetimeMicroSeconds(8L);
    doReturn(existingVsp)
        .when(vspInfoDaoMock).get(any(VspDetails.class));

    VspDetails actualVsp =
        vendorSoftwareProductManager.getVsp(VSP_ID, VERSION01, USER1);

    VspDetails expectedVsp =
        vspInfoDaoMock
            .get(new VspDetails(VSP_ID, VERSION01));
    assertVspsEquals(actualVsp, expectedVsp);
  }

  @Test
  public void testCheckin() {
    doReturn(VERSION01).when(versioningManagerMock)
        .checkin(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1, null);
    Version version = vendorSoftwareProductManager.checkin(VSP_ID, USER1);

    Assert.assertEquals(version, VERSION01);
    verify(versioningManagerMock)
        .checkin(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1, null);
    verify(activityLogManagerMock).addActionLog(activityLogEntityArg.capture(), eq(USER1));
    ActivityLogEntity activityLogEntity = activityLogEntityArg.getValue();
    Assert.assertEquals(activityLogEntity.getVersionId(), String.valueOf(VERSION01.getMajor()+1));
    Assert.assertTrue(activityLogEntity.isSuccess());
  }

  @Test
  public void testCheckout() {
    doReturn(VERSION01).when(versioningManagerMock)
        .checkout(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1);
    Version version = vendorSoftwareProductManager.checkout(VSP_ID, USER1);

    Assert.assertEquals(version, VERSION01);
    verify(versioningManagerMock)
        .checkout(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1);

    verify(activityLogManagerMock).addActionLog(activityLogEntityArg.capture(), eq(USER1));
    ActivityLogEntity activityLogEntity = activityLogEntityArg.getValue();
    Assert.assertEquals(activityLogEntity.getVersionId(), String.valueOf(VERSION01.getMajor()+1));
    Assert.assertTrue(activityLogEntity.isSuccess());
  }


  @Test
  public void testUndoCheckout() {
    Version existingVersion = new Version(0, 2);
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(existingVersion);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE,
        VSP_ID, USER1, VersionableEntityAction.Read);

    doReturn(VERSION01).when(versioningManagerMock).undoCheckout(VendorSoftwareProductConstants
        .VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1);

    VspDetails vsp = new VspDetails(VSP_ID, existingVersion);
    vsp.setName("ExistingName");
    doReturn(vsp).when(vspInfoDaoMock).get(anyObject());
    doNothing().when(vendorSoftwareProductManager).updateUniqueName(vsp.getName(), vsp.getName());

    Version undoCheckoutVersion = vendorSoftwareProductManager.undoCheckout(VSP_ID, USER1);

    Assert.assertEquals(undoCheckoutVersion, VERSION01);
  }


  @Test
  public void testSubmitWithMissingData() throws IOException {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION01);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE,
        VSP_ID, USER1, VersionableEntityAction.Read);

    VspDetails vsp = new VspDetails(VSP_ID, VERSION01);
    doReturn(vsp).when(vspInfoDaoMock).get(anyObject());

    ValidationResponse validationResponse = vendorSoftwareProductManager.submit(VSP_ID, USER1);
    Assert.assertNotNull(validationResponse);
    Assert.assertFalse(validationResponse.isValid());
    List<String> errorIds = validationResponse.getVspErrors().stream().map(ErrorCode::id).distinct()
        .collect(Collectors.toList());
    Assert.assertTrue(errorIds.contains(ValidationErrorBuilder.FIELD_VALIDATION_ERROR_ERR_ID));
    Assert.assertTrue(errorIds.contains(VendorSoftwareProductErrorCodes.VSP_INVALID));

    verify(versioningManagerMock, never())
        .submit(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1, null);
    verify(activityLogManagerMock, never()).addActionLog(any(ActivityLogEntity.class),eq(USER1));
  }

  // TODO: 3/15/2017 fix and enable
  //@Test
  public void testSubmitWithInvalidLicensingData() throws IOException {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION01);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE,
        VSP_ID, USER1, VersionableEntityAction.Read);

    VspDetails vsp =
        createVspDetails(VSP_ID, VERSION01, "Vsp1", "Test-vsp", "vendorName", "vlm1Id", "icon",
            "category", "subCategory", "licenseAgreementId",
            Collections.singletonList("featureGroupId"));
    doReturn(vsp).when(vspInfoDaoMock).get(anyObject());
    UploadDataEntity uploadData = new UploadDataEntity(VSP_ID, VERSION01);
    uploadData.setContentData(
        ByteBuffer.wrap(FileUtils.toByteArray(getFileInputStream("/emptyComposition"))));
    doReturn(uploadData).when(orchestrationTemplateDataDaoMock)
        .getOrchestrationTemplate(anyObject(), anyObject());
    doReturn(new ToscaServiceModel(new FileContentHandler(), new HashMap<>(),
        "MainServiceTemplate.yaml"))
        .when(serviceModelDaoMock).getServiceModel(VSP_ID, VERSION01);

    ValidationResponse validationResponse = vendorSoftwareProductManager.submit(VSP_ID, USER1);
    Assert.assertNotNull(validationResponse);
    Assert.assertFalse(validationResponse.isValid());
    Assert.assertNull(validationResponse.getVspErrors());
    Assert.assertEquals(validationResponse.getLicensingDataErrors(), 1);

    verify(versioningManagerMock, never())
        .submit(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1, null);

    //TODO - check..
    verify(activityLogManagerMock, never()).addActionLog(any(ActivityLogEntity.class), eq(USER1));
  }

  // TODO: 3/15/2017 fix and enable
  //@Test
  public void testSubmit() throws IOException {
    mockVersioning(VersionableEntityAction.Read);

    EnrichmentManagerFactory.getInstance();
    AbstractFactoryBase
        .registerFactory(EnrichmentManagerFactory.class, EnrichmentManagerFactoryImpl.class);

    VspDetails vsp =
        createVspDetails(VSP_ID, VERSION01, "Vsp1", "Test-vsp", "vendorName", "vlm1Id", "icon",
            "category", "subCategory", "123", Collections.singletonList("fg1"));
    doReturn(vsp).when(vspInfoDaoMock).get(anyObject());
    UploadDataEntity uploadData = new UploadDataEntity(VSP_ID, VERSION01);
    uploadData.setContentData(
        ByteBuffer.wrap(FileUtils.toByteArray(getFileInputStream("/emptyComposition"))));
    doReturn(uploadData).when(orchestrationTemplateDataDaoMock)
        .getOrchestrationTemplate(anyObject(), anyObject());
    doReturn(new ToscaServiceModel(new FileContentHandler(), new HashMap<>(),
        "MainServiceTemplate.yaml"))
        .when(serviceModelDaoMock).getServiceModel(VSP_ID, VERSION01);

    ValidationResponse validationResponse = vendorSoftwareProductManager.submit(VSP_ID, USER1);
    Assert.assertTrue(validationResponse.isValid());

/*    Assert.assertEquals(vsp2.getVersionInfo().getActiveVersion(), VERSION10);
    Assert.assertEquals(vsp2.getVersionInfo().getStatus(), VersionStatus.Final);
    Assert.assertNull(vsp2.getVersionInfo().getLockingUser());*/

    verify(versioningManagerMock)
        .submit(VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID,
            USER1, null);
    verify(activityLogManagerMock).addActionLog(activityLogEntityArg.capture(),eq(USER1));
    ActivityLogEntity activityLogEntity = activityLogEntityArg.getValue();
    Assert.assertEquals(activityLogEntity.getVersionId(), String.valueOf(VERSION10.getMajor()));
    Assert.assertTrue(activityLogEntity.isSuccess());
  }

  @Test(expectedExceptions = CoreException.class)
  public void testCreatePackageOnNonFinalVersion_negative() throws IOException {
    vendorSoftwareProductManager.createPackage(VSP_ID, VERSION01, USER1);
  }

  @Test
  public void testCreatePackage() throws IOException {
    /*VspDetails vspDetailsMock = new VspDetails("vspId", new Version(1, 0));
    doReturn(vspDetailsMock).when(vspInfoDaoMock).get(anyObject());*/
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION10);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1,
        VersionableEntityAction.Read);

    doReturn(new ToscaServiceModel(new FileContentHandler(), new HashMap<>(), "")).when
        (enrichedServiceModelDaoMock).getServiceModel(VSP_ID, VERSION10);

    VspDetails vsp = new VspDetails(VSP_ID, VERSION10);
    vsp.setVendorId("vendorId");
    vsp.setVlmVersion(VERSION10);
    vsp.setFeatureGroups(Arrays.asList("fg1", "fg2"));
    doReturn(vsp).when(vspInfoDaoMock).get(any(VspDetails.class));

    doReturn(new FileContentHandler()).when(licenseArtifactsServiceMock)
        .createLicenseArtifacts(VSP_ID, vsp.getVendorId(), VERSION10, vsp.getFeatureGroups(),
            USER1);

    PackageInfo packageInfo = vendorSoftwareProductManager.createPackage(VSP_ID, VERSION10, USER1);
    Assert.assertNotNull(packageInfo.getVspId());
  }

  // TODO: 3/15/2017 fix and enable
  //@Test(dependsOnMethods = {"testListFinals"})
  public void testUploadFileMissingFile() {
    InputStream zis = getFileInputStream("/vspmanager/zips/missingYml.zip");

    UploadFileResponse uploadFileResponse =
        candidateManager.upload(VSP_ID, VERSION01, zis, USER1);

    Assert.assertEquals(uploadFileResponse.getErrors().size(), 0);
  }

  // TODO: 3/15/2017 fix and enable
  //@Test(dependsOnMethods = {"testUploadFileMissingFile"})
  public void testUploadNotZipFile() throws IOException {
    URL url = this.getClass().getResource("/notZipFile");

    try {
      candidateManager
          .upload(VSP_ID, VERSION01,
              url.openStream(), USER1);
      candidateManager.process(VSP_ID, VERSION01, USER1);
    } catch (Exception ce) {
      Assert.assertEquals(ce.getMessage(), Messages.CREATE_MANIFEST_FROM_ZIP.getErrorMessage());
    }

    verify(activityLogManagerMock, never()).addActionLog(any(ActivityLogEntity.class),eq(USER1));
  }
/*
  @Test
  public void testEnrichModelInSubmit() {
    UniqueValueUtil
        .deleteUniqueValue(VendorSoftwareProductConstants.UniqueValues.VENDOR_SOFTWARE_PRODUCT_NAME,
            "VSP_syb");
    VspDetails vspDetails = vendorSoftwareProductManager.createVsp(
        createVspDetails(null, null, "VSP_syb", "Test-vsp_syb", "vendorName", "vlm1Id", "icon",
            "category", "subCategory", "456", null), USER1);
    String id = vspDetails.getId();

    //upload file
    InputStream zis = getFileInputStream("/vspmanager/zips/fullComposition.zip");
    candidateManager.upload(id, VERSION01, zis, USER1);
    OrchestrationTemplateActionResponse uploadFileResponse =
        candidateManager.process(id, VERSION01, USER1);

    //check in
    vendorSoftwareProductManager.checkin(id, USER1);
    //submit
    try {
      ValidationResponse result = vendorSoftwareProductManager.submit(id, USER1);
    } catch (IOException exception) {
      Assert.fail();
    }
    VersionedVendorSoftwareProductInfo details =
        vendorSoftwareProductManager.getVsp(id, null, USER1);
    Collection<ComponentEntity> components =vendorSoftwareProductManager
        .listComponents(id, details.getVersionInfo().getActiveVersion(), USER1);

    ToscaServiceModel model =
        (ToscaServiceModel) EnrichedServiceModelDaoFactory.getInstance().createInterface()
            .getServiceModel(id, details.getVersionInfo().getActiveVersion());

    Map<String, CapabilityDefinition> capabilities = new HashMap<>();
    for (ComponentEntity component : components) {
      model.getServiceTemplates().
          entrySet().
          stream().
          filter(entryValue -> entryValue.getValue() != null &&
              entryValue.getValue().getNode_types() != null &&
              entryValue.getValue().
                  getNode_types().
                  containsKey(component.getComponentCompositionData().getName())).
          forEach(entryValue -> entryValue.getValue().getNode_types().
              values().
              stream().
              filter(type -> MapUtils.isNotEmpty(type.getCapabilities())).
              forEach(type -> type.getCapabilities().
                  entrySet().
                  forEach(entry -> addCapability(entryValue.getKey(), capabilities, entry.getKey(),
                      entry.getValue()))));

    }

    Assert.assertNotNull(capabilities);
  }

  @Test(dependsOnMethods = {"testCreatePackage"})
  public void testEnrichedFilesDeletedOnNewUpload() throws IOException {
    Version activeVersion;

    createPackageFromUpload(VSP_ID, USER1, "/fullComposition");
    activeVersion = vendorSoftwareProductManager.getVsp(VSP_ID, null, USER1).getVersionInfo()
        .getActiveVersion();

    List<ServiceArtifact> firstExternalArtifacts = enrichedServiceModelDaoMock
        .getExternalArtifacts(VSP_ID, activeVersion);
    ToscaServiceModel firstServiceModel = enrichedServiceModelDaoMock.getServiceModel(VSP_ID,
        activeVersion);

    createPackageFromUpload(VSP_ID, USER1, "/emptyComposition");
    activeVersion = vendorSoftwareProductManager.getVsp(VSP_ID, null, USER1).getVersionInfo()
        .getActiveVersion();

    List<ServiceArtifact> secondExternalArtifacts = enrichedServiceModelDaoMock
        .getExternalArtifacts(VSP_ID, activeVersion);
    ToscaServiceModel secondServiceModel = enrichedServiceModelDaoMock.getServiceModel(VSP_ID,
        activeVersion);

    Assert.assertNotEquals(firstExternalArtifacts, secondExternalArtifacts);
    Assert.assertNotEquals(firstServiceModel, secondServiceModel);

  }

  @Test(dependsOnMethods = {"testMibsDeletedInCsar"})
  public void testServiceTemplatesAreDeletedInCsarOnNewUpload() throws IOException {
    String nestedPath = "Definitions" + File.separator + "nested";

    uploadFileAndProcess(VSP_ID, USER1, "/vspmanager/zips/fullCompositionNested.zip");
    checkinSubmitCreatePackage(VSP_ID, USER1);
    List<String> nestedFileNamesServiceTemplates =
        getWantedFileNamesFromCsar(nestedPath);

    uploadFileAndProcess(VSP_ID, USER1, "/vspmanager/zips/fullComposition.zip");
    checkinSubmitCreatePackage(VSP_ID, USER1);
    List<String> emptyNestedNamesList = getWantedFileNamesFromCsar(nestedPath);

    Assert.assertEquals(emptyNestedNamesList.size(), 0);
    Assert.assertNotEquals(emptyNestedNamesList.size(), nestedFileNamesServiceTemplates.size());
  }*/

  private List<String> getWantedFileNamesFromCsar(String pathInCsar)
      throws IOException {
    File translatedFile = vendorSoftwareProductManager.getTranslatedFile(VSP_ID, VERSION10, USER1);

    return getFileNamesFromFolderInCsar(translatedFile,
        pathInCsar);
  }

  private List<String> getFileNamesFromFolderInCsar(File csar, String folderName)
      throws IOException {
    List<String> fileNames = new ArrayList<>();

    ZipInputStream zip = new ZipInputStream(new FileInputStream(csar));
    ZipEntry ze;

    while ((ze = zip.getNextEntry()) != null) {
      String name = ze.getName();
      if (name.contains(folderName)) {
        fileNames.add(name);
      }
    }

    return fileNames;
  }

  private Pair<String, String> uploadMib(String vspId, String user, String filePath,
                                         String fileName) {
    List<ComponentEntity> componentEntities = null;
    //(List<ComponentEntity>) vendorSoftwareProductManager.listComponents(vspId, null, user);
    mibManager.upload(getFileInputStream(filePath),
        fileName, vspId,
        VERSION01, componentEntities.get(0).getId(), ArtifactType.SNMP_POLL, user);
    //TODO: add validate of addActionLog() func call

    return new ImmutablePair<>(componentEntities.get(0).getId(),
        componentEntities.get(0).getComponentCompositionData()
            .getDisplayName());
  }

  private void createPackageFromUpload(String vspId, String user, String filePath)
      throws IOException {
    uploadFileAndProcess(vspId, user, filePath);
    checkinSubmitCreatePackage(vspId, user);
  }

  private void uploadFileAndProcess(String vspId, String user, String filePath) {
    vendorSoftwareProductManager.checkout(vspId, user);
    candidateManager.upload(vspId, VERSION01, getFileInputStream(filePath), user);
    candidateManager.process(vspId, VERSION01, user);
  }

  private void checkinSubmitCreatePackage(String vspId, String user) throws IOException {
    vendorSoftwareProductManager.checkin(vspId, user);
    ValidationResponse submitResponse = vendorSoftwareProductManager.submit(vspId, user);
    Assert.assertTrue(submitResponse.isValid());
    vendorSoftwareProductManager.createPackage(vspId, VERSION10, user);
  }

  // TODO: 3/15/2017 fix and enable
/*

  public void testUpdatedVSPShouldBeInBeginningOfList() {
    vendorSoftwareProductManager.updateVsp(new VspDetails(), USER3);
    assertVSPInWantedLocationInVSPList(id006, 0, USER3);

    InputStream zis = getFileInputStream("/vspmanager/zips/fullComposition.zip");
    candidateManager.upload(id007, VERSION01, zis, USER3);
    candidateManager.process(id007, VERSION01, USER3);
    assertVSPInWantedLocationInVSPList(id007, 0, USER3);
  }

  @Test(dependsOnMethods = {"testUpdatedVSPShouldBeInBeginningOfList"})
  public void testVSPInBeginningOfListAfterCheckin() {
    vendorSoftwareProductManager.checkin(id006, USER3);
    assertVSPInWantedLocationInVSPList(id006, 0, USER3);

    vendorSoftwareProductManager.checkin(id007, USER3);
    assertVSPInWantedLocationInVSPList(id007, 0, USER3);
  }

  @Test(dependsOnMethods = {"testVSPInBeginningOfListAfterCheckin"})
  public void testVSPInBeginningOfListAfterCheckout() {
    vendorSoftwareProductManager.checkout(id006, USER3);
    assertVSPInWantedLocationInVSPList(id006, 0, USER3);
  }

  @Test(dependsOnMethods = {"testVSPInBeginningOfListAfterCheckout"})
  public void testVSPInBeginningOfListAfterUndoCheckout() {
    vendorSoftwareProductManager.checkout(id007, USER3);
    assertVSPInWantedLocationInVSPList(id007, 0, USER3);

    vendorSoftwareProductManager.undoCheckout(id006, USER3);
    assertVSPInWantedLocationInVSPList(id006, 0, USER3);
  }

  @Test(dependsOnMethods = {"testVSPInBeginningOfListAfterUndoCheckout"})
  public void testVSPInBeginningOfListAfterSubmit() throws IOException {
    vendorSoftwareProductManager.checkin(id007, USER3);
    vendorSoftwareProductManager.submit(id007, USER3);

    assertVSPInWantedLocationInVSPList(id007, 0, USER3);
  }
*/

  private void testLegalUpload(String vspId, Version version, InputStream upload, String user) {
    candidateManager.upload(vspId, VERSION01, upload, USER1);
    candidateManager.process(vspId, VERSION01, user);

    UploadDataEntity uploadData =
        orchestrationTemplateDataDaoMock.getOrchestrationTemplate(vspId, version);
    Assert.assertNotNull(uploadData);
  }

  private void addCapability(String entryValueKey, Map<String, CapabilityDefinition> capabilities,
                             String key, CapabilityDefinition value) {

    capabilities.put(entryValueKey + "_" + key, value);
  }

  public InputStream getFileInputStream(String fileName) {
    URL url = this.getClass().getResource(fileName);
    try {
      return url.openStream();
    } catch (IOException exception) {
      exception.printStackTrace();
      return null;
    }
  }

  private void assertVSPInWantedLocationInVSPList(String vspId, int location, String user) {
    List<VersionedVendorSoftwareProductInfo> vspList =
        vendorSoftwareProductManager.listVsps(null, user);
    Assert.assertEquals(vspList.get(location).getVspDetails().getId(), vspId);
  }


  //  private void assertInfoArtifactIsInRightPathInCsar(String vspId, String zipFileName)
//      throws IOException {
//    ZipInputStream inputZipStream = new ZipInputStream(new FileInputStream(new File(zipFileName)));
//    boolean isInfoArtifactInZip = false;
//
//    ZipEntry zipEntry;
//    while ((zipEntry = inputZipStream.getNextEntry()) != null) {
//      String currentEntryName = zipEntry.getName();
//      if(currentEntryName.equals("Artifacts\\Informative\\Guide\\VSP_" +
//          vspId + "_Information.txt")){
//        isInfoArtifactInZip = true;
//        break;
//      }
//    }
//
//    Assert.assertTrue(isInfoArtifactInZip);
//  }
  static VspDetails createVspDetails(String id, Version version, String name, String desc,
                                     String vendorName, String vlm, String icon,
                                     String category, String subCategory,
                                     String licenseAgreement, List<String> featureGroups) {
    VspDetails vspDetails = new VspDetails(id, version);
    vspDetails.setName(name);
    vspDetails.setDescription(desc);
    vspDetails.setIcon(icon);
    vspDetails.setCategory(category);
    vspDetails.setSubCategory(subCategory);
    vspDetails.setVendorName(vendorName);
    vspDetails.setVendorId(vlm);
    vspDetails.setVlmVersion(new Version(1, 0));
    vspDetails.setLicenseAgreement(licenseAgreement);
    vspDetails.setFeatureGroups(featureGroups);
    return vspDetails;
  }

  static void assertVspsEquals(VspDetails actual, VspDetails expected) {
    Assert.assertEquals(actual.getId(), expected.getId());
    Assert.assertEquals(actual.getVersion(), expected.getVersion());
    Assert.assertEquals(actual.getName(), expected.getName());
    Assert.assertEquals(actual.getDescription(), expected.getDescription());
    Assert.assertEquals(actual.getIcon(), expected.getIcon());
    Assert.assertEquals(actual.getCategory(), expected.getCategory());
    Assert.assertEquals(actual.getSubCategory(), expected.getSubCategory());
    Assert.assertEquals(actual.getVendorName(), expected.getVendorName());
    Assert.assertEquals(actual.getVendorId(), expected.getVendorId());
    Assert.assertEquals(actual.getLicenseAgreement(), expected.getLicenseAgreement());
    Assert.assertEquals(actual.getFeatureGroups(), expected.getFeatureGroups());
  }


//    @Test
//    public void testDownloadFile() throws IOException {
//        VspDetails expectedVsp = VSPCommon.createVspDetails(null, null, String.format("VSP-test-%s", vlm1Id), "Test-vsp", "vendorName", "vlm1Id", "icon", "category", "subCategory", "123", null);
//        VspDetails createdVsp = vendorSoftwareProductManager.createVsp(expectedVsp, USER1);
//
//        id005 = createdVsp.getId();
//        Assert.assertNotNull(id005);
//        Assert.assertNotNull(createdVsp.getVersion());
//
//        try (InputStream zipInputStream = new ZipFileUtils().getZipInputStream("/legalUploadWithWarning")) {
//
//            UploadFileResponse uploadFileResponse = vendorSoftwareProductManager.upload(id005, zipInputStream, USER1);
//            vendorSoftwareProductManager.process(id005, USER1);
//            Optional<File> fileCandidate = vendorSoftwareProductManager.get(id005, USER1);
//
//            File latestHeatPackage = fileCandidate.get();
//
//            zipInputStream.reset();
//            byte[] uploaded = IOUtils.toByteArray(zipInputStream);
//
//            Optional<FileContentHandler> zipContentMap = vendorSoftwareProductManager.getZipContentMap(uploadFileResponse, uploaded);
//            FileContentHandler fileContentHandler = new FileContentHandler();
//            if(zipContentMap.isPresent()){
//                 fileContentHandler = zipContentMap.get();
//            }
//
//            uploaded = IOUtils.toByteArray(fileContentHandler.getFiles().values());
//
//            byte[] downloaded;
//            try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(latestHeatPackage))) {
//                downloaded = IOUtils.toByteArray(fileStream);
//            }
//
//            Assert.assertTrue(Arrays.equals(uploaded, downloaded));
//        }
//    }

  // todo ********************** move to common **************************************

  private void mockVersioning(VersionableEntityAction action) {
    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setActiveVersion(VERSION01);
    doReturn(versionInfo).when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1,
        action);
  }

  private void mockVersioningEntityNotExist(VersionableEntityAction action, String vspId) {
    doThrow(new CoreException(new EntityNotExistErrorBuilder(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, vspId).build()))
        .when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, vspId, USER1,
        action);
  }

  private void MockVersioningEntityLocked(VersionableEntityAction action) {
    doThrow(new CoreException(new EditOnEntityLockedByOtherErrorBuilder(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER1)
        .build()))
        .when(versioningManagerMock).getEntityVersionInfo(
        VendorSoftwareProductConstants.VENDOR_SOFTWARE_PRODUCT_VERSIONABLE_TYPE, VSP_ID, USER2,
        action);
  }
}