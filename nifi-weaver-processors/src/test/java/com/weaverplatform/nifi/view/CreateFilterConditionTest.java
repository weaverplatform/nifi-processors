package com.weaverplatform.nifi.view;

import com.google.common.io.Resources;
import com.weaverplatform.nifi.util.WeaverProperties;
import com.weaverplatform.sdk.Entity;
import com.weaverplatform.sdk.EntityType;
import com.weaverplatform.sdk.RelationKeys;
import com.weaverplatform.sdk.Weaver;
import com.weaverplatform.sdk.websocket.WeaverSocket;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.NiFiProperties;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class CreateFilterConditionTest {

  private TestRunner testRunner;

  private Weaver weaver;
  private Entity dataset;
  private Entity view;
  private Entity filter;
  private Entity individual;
  
  private static String WEAVER_URL;    
  private static String WEAVER_DATASET;
  
  @BeforeClass
  public static void beforeClass() throws IOException {
    
    // Define property file for NiFi
    Properties props = System.getProperties();
    props.setProperty("nifi.properties.file.path", Resources.getResource("nifi.properties").getPath());

    // Read test properties
    Properties testProperties = new Properties();
    testProperties.load(Resources.getResource("test.properties").openStream());
    WEAVER_URL     = testProperties.get("weaver.url").toString();
    WEAVER_DATASET = testProperties.get("weaver.global.dataset").toString();
    
    // Set Nifi Weaver properties
    NiFiProperties.getInstance().put(WeaverProperties.URL, WEAVER_URL);
    NiFiProperties.getInstance().put(WeaverProperties.DATASET, WEAVER_DATASET);
  }
  
  @Before
  public void init() throws URISyntaxException {
    testRunner = TestRunners.newTestRunner(CreateFilterCondition.class);
    
    // Wipe weaver database first
    weaver = new Weaver();
    weaver.connect(new WeaverSocket(new URI(WEAVER_URL)));
    weaver.wipe();
    
    // Create dataset
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("name", "Dataset");
    dataset = weaver.add(attributes, EntityType.DATASET, WEAVER_DATASET);
    
    // Give it the minimal collections it needs to be qualified as a valid view
    dataset.linkEntity("views", weaver.collection());
    dataset.linkEntity(RelationKeys.OBJECTS, weaver.collection());
    
    // Create view
    Map<String, Object> viewAttributes = new HashMap<>();
    attributes.put("name", "View");
    view = weaver.add(attributes, EntityType.VIEW);

    // Give it the minimal collections it needs to be qualified as a valid view
    view.linkEntity("filters", weaver.collection());
    view.linkEntity(RelationKeys.OBJECTS, weaver.collection());

    // Attach to dataset views
    Entity views = weaver.get(dataset.getRelations().get("views").getId());
    views.linkEntity(view.getId(), view);
    
    // Create filter
    Map<String, Object> filterAttributes = new HashMap<>();
    filterAttributes.put("label", "Type");
    filterAttributes.put("celltype", "individual");
    filterAttributes.put("predicate", "rdf:type");
    filter = weaver.add(filterAttributes, "$FILTER");

    // Give it the minimal collections it needs to be qualified as a valid view
    filter.linkEntity("conditions", weaver.collection());
    
    // Attach to view
    Entity filters = weaver.get(view.getRelations().get("filters").getId());
    filters.linkEntity(filter.getId(), filter);
    
    // Create individual for condition
    Map<String, Object> individualAttributes = new HashMap<>();
    individualAttributes.put("name", "Tree");
    individual = weaver.add(individualAttributes, EntityType.INDIVIDUAL, "weav:Tree");

    // Give it the minimal collections it needs to be qualified as a valid individual
    individual.linkEntity(RelationKeys.PROPERTIES, weaver.collection());
    individual.linkEntity(RelationKeys.ANNOTATIONS, weaver.collection());
    
    // Attach to dataset objects
    Entity objects = weaver.get(dataset.getRelations().get("objects").getId());
    objects.linkEntity(individual.getId(), individual);
  }
  

  @Test
  public void testFilterConditionCreation() throws URISyntaxException {
    ProcessSession session = testRunner.getProcessSessionFactory().createSession();
    
    // Set properties
    testRunner.setProperty(CreateFilterCondition.FILTER_ID_ATTRIBUTE, "filterId");
    testRunner.setProperty(CreateFilterCondition.CONDITION_TYPE_STATIC, "individual");
    testRunner.setProperty(CreateFilterCondition.OPERATION_STATIC, "this-individual");
    testRunner.setProperty(CreateFilterCondition.INDIVIDUAL_STATIC, "weav:Tree");
    testRunner.setProperty(CreateFilterCondition.ATTRIBUTE_NAME_FOR_CONDITION_ID, "conditionId");

    // Create flowFile with content
    FlowFile flowFile = session.create();
    flowFile = session.importFrom(new ByteArrayInputStream("Flowfile Content".getBytes()), flowFile);
    
    // Set attributes
    flowFile = session.putAttribute(flowFile, "filterId", filter.getId());

    // Add the flowfile to the runner
    testRunner.enqueue(flowFile);

    // Run the enqueued content, it also takes an int = number of contents queued
    testRunner.run();

    // Get result
    List<MockFlowFile> results = testRunner.getFlowFilesForRelationship(CreateFilter.ORIGINAL);
    assertTrue("1 match", results.size() == 1);
    MockFlowFile result = results.get(0);

    String conditionId = result.getAttribute("conditionId");

    // Assert Filter is created in Weaver
    weaver = new Weaver();
    weaver.connect(new WeaverSocket(new URI(WEAVER_URL)));

    // Load Filter
    Entity reloadedFilter = weaver.get(filter.getId());
    assertEquals(reloadedFilter.getAttributes().get("label"), "Type");
    assertNotNull(reloadedFilter.getRelations().get("conditions"));
    
    Entity conditions = weaver.get(reloadedFilter.getRelations().get("conditions").getId());
    assertNotNull(conditions.getRelations().get(conditionId));
    
    // Load Condition
    Entity condition = weaver.get(conditionId);
    assertEquals(condition.getAttributes().get("conditiontype"), "individual");
    assertEquals(condition.getAttributes().get("operation"), "this-individual");
    assertEquals(condition.getRelations().get("individual").getId(), individual.getId());
  }
}