/**
 * Mule Anypoint Template
 * Copyright (c) MuleSoft, Inc.
 * All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.construct.Flow;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is to validate the correct behavior of the Mule Template that make calls to external systems.
 * 
 * The test will invoke the batch process and afterwards check that the opportunities had been correctly created and that the ones that should be filtered are
 * not in the destination sand box.
 * 
 * The test validates that an account will get sync as result of the integration.
 */
public class BusinessLogicTestCreateAccountIT extends AbstractTemplateTestCase {

	private static final String FLOW_NAME = "triggerFlow";
	private BatchTestHelper helper;
	private List<Map<String, Object>> createdOpportunities = new ArrayList<Map<String, Object>>();
	private List<Map<String, Object>> createdAccounts = new ArrayList<Map<String, Object>>();

	@BeforeClass
	public static void init() {

		System.setProperty("page.size", "1000");

		// Set the frequency between polls to 10 seconds
		System.setProperty("poll.frequencyMillis", "10000");

		// Set the poll starting delay to 20 seconds
		System.setProperty("poll.startDelayMillis", "20000");

		// Setting Default Watermark Expression to query SFDC with
		// LastModifiedDate greater than ten seconds before current time
		System.setProperty("watermark.default.expression", "#[groovy: new Date(System.currentTimeMillis() - 10000).format(\"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'\", TimeZone.getTimeZone('UTC'))]");

		System.setProperty("account.sync.policy", "syncAccount");
		System.setProperty("account.id.in.b", "");

	}

	@AfterClass
	public static void shutDown() {
		System.clearProperty("account.sync.policy");
	}

	@Before
	public void setUp() throws Exception {
		helper = new BatchTestHelper(muleContext);
		retrieveSalesOrderFromSapFlow = lookupFlowConstruct("retrieveSalesOrderFromSapFlow");
		retrieveAccountFromSapFlow = lookupFlowConstruct("retrieveAccountFromSapFlow");
		createTestDataInSandBox();
	}


	@After
	public void tearDown() throws Exception {
		deleteTestDataFromSandBox();
	}

	@Test
	public void testMainFlow() throws Exception {

		// Run flow and wait for it to run
		runFlow(FLOW_NAME);

		// Wait for the batch job executed by the poll flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();

		Assert.assertEquals("The opportunity should not have been sync", null, invokeRetrieveFlow(retrieveSalesOrderFromSapFlow, createdOpportunities.get(0)));

		Assert.assertEquals("The opportunity should not have been sync", null, invokeRetrieveFlow(retrieveSalesOrderFromSapFlow, createdOpportunities.get(1)));

		Map<String, Object> accountPayload = invokeRetrieveFlow(retrieveAccountFromSapFlow, createdAccounts.get(0));
		Map<String, Object> opportunityPayload = invokeRetrieveFlow(retrieveSalesOrderFromSapFlow, createdOpportunities.get(2));
		Assert.assertEquals("The opportunity should have been sync", createdOpportunities.get(2).get("Name"), opportunityPayload == null ? null : opportunityPayload.get("Name"));
		Assert.assertEquals("The opportunity should belong to a different account ", accountPayload.get("CustomerNumber"), opportunityPayload == null ? null : opportunityPayload.get("AccountId"));
	}

	private void createTestDataInSandBox() throws MuleException, Exception {
		createAccounts();
		createOpportunities();
	}

	@SuppressWarnings("unchecked")
	private void createAccounts() throws Exception {
		Flow flow = lookupFlowConstruct("createAccountInSalesforceFlow");
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("Name", "sfdc2sap_opp_mg_" + Long.toString(System.currentTimeMillis(), Character.MAX_RADIX));
		map.put("BillingCity", "San Francisco");
		map.put("BillingCountry", "USA");
		map.put("Phone", "123456789");
		map.put("Industry", "Education");
		map.put("NumberOfEmployees", 9000);
		
		createdAccounts.add(map);

		MuleEvent event = flow.process(getTestEvent(createdAccounts, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> results = (List<SaveResult>) event.getMessage().getPayload();
		for (int i = 0; i < results.size(); i++) {
			createdAccounts.get(i).put("Id", results.get(i).getId());
		}

		System.out.println("Results of data creation in sandbox" + createdAccounts.toString());
	}

	@SuppressWarnings("unchecked")
	private void createOpportunities() throws Exception {
		Flow flow = lookupFlowConstruct("createOpportunityInSalesforceFlow");

		// This opportunity should not be sync
		Map<String, Object> opportunity = createOpportunity("Salesforce", 0);
		opportunity.put("Amount", 300);
		createdOpportunities.add(opportunity);

		// This opportunity should not be sync
		opportunity = createOpportunity("Salesforce", 1);
		opportunity.put("Amount", 1000);
		createdOpportunities.add(opportunity);

		// This opportunity should BE sync with it's account
		opportunity = createOpportunity("Salesforce", 2);
		opportunity.put("Amount", 30000);
		opportunity.put("AccountId", createdAccounts.get(0).get("Id"));
		opportunity.put("StageName", "Closed Won");
		createdOpportunities.add(opportunity);

		MuleEvent event = flow.process(getTestEvent(createdOpportunities, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> results = (List<SaveResult>) event.getMessage().getPayload();
		for (int i = 0; i < results.size(); i++) {
			createdOpportunities.get(i).put("Id", results.get(i).getId());
		}
	}

	private void deleteTestDataFromSandBox() throws MuleException, Exception {
		deleteTestOpportunityFromSandBox(createdOpportunities);
		deleteTestAccountFromSandBox(createdAccounts);
	}

}
