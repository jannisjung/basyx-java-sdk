/*******************************************************************************
 * Copyright (C) 2021 the Eclipse BaSyx Authors
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.basyx.testsuite.regression.aas.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;

import org.eclipse.basyx.aas.aggregator.api.IAASAggregator;
import org.eclipse.basyx.aas.aggregator.restapi.AASAggregatorProvider;
import org.eclipse.basyx.aas.manager.ConnectedAssetAdministrationShellManager;
import org.eclipse.basyx.aas.metamodel.api.IAssetAdministrationShell;
import org.eclipse.basyx.aas.metamodel.api.parts.asset.AssetKind;
import org.eclipse.basyx.aas.metamodel.map.AssetAdministrationShell;
import org.eclipse.basyx.aas.metamodel.map.descriptor.ModelUrn;
import org.eclipse.basyx.aas.metamodel.map.parts.Asset;
import org.eclipse.basyx.aas.registration.memory.InMemoryRegistry;
import org.eclipse.basyx.submodel.metamodel.api.identifier.IdentifierType;
import org.eclipse.basyx.submodel.metamodel.map.Submodel;
import org.eclipse.basyx.submodel.metamodel.map.identifier.Identifier;
import org.eclipse.basyx.submodel.metamodel.map.qualifier.LangStrings;
import org.eclipse.basyx.vab.exception.provider.ResourceNotFoundException;
import org.eclipse.basyx.vab.modelprovider.api.IModelProvider;
import org.eclipse.basyx.vab.protocol.api.IConnectorFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Testsuite for implementations of the IAASAggregator interface
 * 
 * @author conradi, schnicke
 *
 */
public abstract class AASAggregatorSuite {

	protected AssetAdministrationShell aas1;

	// Choose AAS Id that needs encoding due to '/'
	private static final String aas1Id = "aas1/s";
	private static final LangStrings description1 = new LangStrings("en", "This is test AAS 1");
	private static final String aas1Category = "TestCategory1";
	private static final String aas1AltCategory = "OtherTestCategory1";

	protected AssetAdministrationShell aas2;
	private static final String aas2Id = "aas2";
	private static final LangStrings description2 = new LangStrings("en", "This is test AAS 2");
	private static final String aas2Category = "TestCategory2";

	// initializing dummy test data
	@Before
	public void initAASDummies() {
		aas1 = new AssetAdministrationShell(aas1Id, new Identifier(IdentifierType.CUSTOM, aas1Id), new Asset("asset1IdShort", new Identifier(IdentifierType.CUSTOM, "asset1"), AssetKind.INSTANCE));
		aas1.setDescription(description1);
		aas1.setCategory(aas1Category);

		aas2 = new AssetAdministrationShell(aas2Id, new Identifier(IdentifierType.CUSTOM, aas2Id), new Asset("asset2IdShort", new Identifier(IdentifierType.CUSTOM, "asset2"), AssetKind.INSTANCE));
		aas2.setDescription(description2);
		aas2.setCategory(aas2Category);
	}

	protected abstract IAASAggregator getAggregator();

	@Before
	public void clearAASAggregator() {
		IAASAggregator aggregator = getAggregator();
		aggregator.getAASList().stream().map(a -> a.getIdentification()).forEach(id -> aggregator.deleteAAS(id));
	}

	@Test
	public void testCreateAndGetAAS() {
		IAASAggregator aggregator = getAggregator();

		// create a new AAS
		aggregator.createAAS(aas1);

		// get and check the created AAS
		IAssetAdministrationShell retrieved = aggregator.getAAS(aas1.getIdentification());
		checkAAS1(retrieved);
	}

	@Test
	public void testGetAASList() throws Exception {
		IAASAggregator aggregator = getAggregator();

		// Create two AASs
		aggregator.createAAS(aas1);
		aggregator.createAAS(aas2);

		// get the collection of all AASs
		Collection<IAssetAdministrationShell> coll = aggregator.getAASList();
		assertEquals(2, coll.size());

		// check the AAS collection
		for (IAssetAdministrationShell aas : coll) {
			if (aas.getIdShort().equals(aas1Id)) {
				checkAAS1(aas);
			} else if (aas.getIdShort().equals(aas2Id)) {
				checkAAS2(aas);
			} else {
				fail();
			}
		}
	}

	@Test
	public void testUpdate() throws Exception {
		IAASAggregator aggregator = getAggregator();

		ModelUrn aasUrn = new ModelUrn(aas1Id);

		// Create a new AAS
		aggregator.createAAS(aas1);

		// Get and check the unchanged AAS
		checkAAS1(aggregator.getAAS(aasUrn));

		// Change category of AAS locally
		aas1.setCategory(aas1AltCategory);

		// Update the changed AAS
		aggregator.updateAAS(aas1);

		// Get the updated AAS and check its category
		IAssetAdministrationShell aas = aggregator.getAAS(aasUrn);
		assertEquals(aas1AltCategory, aas.getCategory());

		// Create ConnectedAASManager
		ConnectedAssetAdministrationShellManager manager = new ConnectedAssetAdministrationShellManager(new InMemoryRegistry(), new IConnectorFactory() {

			@Override
			public IModelProvider getConnector(String addr) {
				return new AASAggregatorProvider(aggregator);
			}
		});
		manager.createAAS(aas1, "");
		// Create Submodel
		String submodelId = "testSm";
		Identifier submodelIdentifier = new Identifier(IdentifierType.CUSTOM, "testSmIdentifier");
		Submodel submodel = new Submodel(submodelId, submodelIdentifier);
		manager.createSubmodel(aasUrn, submodel);

		aggregator.updateAAS(aas1);

		assertEquals(submodel.getIdShort(), manager.retrieveSubmodel(aasUrn, submodelIdentifier).getIdShort());
	}

	@Test
	public void testDelete() throws Exception {
		IAASAggregator aggregator = getAggregator();

		// Create two new AASs
		aggregator.createAAS(aas1);
		aggregator.createAAS(aas2);

		// Get AAS collection and check, that both are present
		Collection<IAssetAdministrationShell> coll = aggregator.getAASList();
		assertEquals(2, coll.size());

		// Delete one of the AASs
		aggregator.deleteAAS(new ModelUrn(aas1Id));

		// Get AAS collection and check, that one of them is deleted
		coll = aggregator.getAASList();
		assertEquals(1, coll.size());

		for (IAssetAdministrationShell aas : coll) {
			if (aas.getIdShort().equals(aas1Id)) { // aas1 should be deleted
				fail();
			} else if (aas.getIdShort().equals(aas2Id)) {
				checkAAS2(aas);
			} else {
				fail();
			}
		}
	}

	@Test(expected = ResourceNotFoundException.class)
	public void deleteNotExistingAAS() {
		getAggregator().getAAS(new Identifier(IdentifierType.CUSTOM, "IDontExist"));
	}

	@After
	public void deleteExistingAAS() {
		IAASAggregator aggregator = getAggregator();

		// Delete aas1 if exists
		try {
			aggregator.deleteAAS(new ModelUrn(aas1Id));
		} catch (ResourceNotFoundException e) {
			// do nothing
		}

		// Delete aas2 if exists
		try {
			aggregator.deleteAAS(new ModelUrn(aas2Id));
		} catch (ResourceNotFoundException e) {
			// do nothing
		}
	}

	// Methods to verify, that AAS objects contain the correct test data
	private void checkAAS1(Object o) {
		assertTrue(o instanceof IAssetAdministrationShell);
		IAssetAdministrationShell aas = (IAssetAdministrationShell) o;

		assertEquals(aas1Id, aas.getIdShort());
		assertEquals(aas1Id, aas.getIdentification().getId());
		assertEquals(description1.get("en"), aas.getDescription().get("en"));
		assertEquals(aas1Category, aas.getCategory());
	}

	private void checkAAS2(Object o) {
		assertTrue(o instanceof IAssetAdministrationShell);
		IAssetAdministrationShell aas = (IAssetAdministrationShell) o;

		assertEquals(aas2Id, aas.getIdShort());
		assertEquals(aas2Id, aas.getIdentification().getId());
		assertEquals(description2.get("en"), aas.getDescription().get("en"));
		assertEquals(aas2Category, aas.getCategory());
	}

}
