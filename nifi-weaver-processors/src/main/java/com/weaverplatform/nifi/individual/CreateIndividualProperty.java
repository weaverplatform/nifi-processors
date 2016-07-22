package com.weaverplatform.nifi.individual;

import com.weaverplatform.sdk.*;
import com.weaverplatform.sdk.json.request.ReadPayload;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Tags({"weaver, create, individualproperty"})
@CapabilityDescription("Creates an individual property")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class CreateIndividualProperty extends FlowFileProcessor {

  public static final PropertyDescriptor SUBJECT_ATTRIBUTE = new PropertyDescriptor
    .Builder().name("Subject Attribute")
    .description("Look for the FlowFile attribute.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor SUBJECT_STATIC = new PropertyDescriptor
    .Builder().name("Subject Static")
    .description("If there is no FlowFile attribute, use static value.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor PREDICATE_ATTRIBUTE = new PropertyDescriptor
    .Builder().name("Predicate Attribute")
    .description("Look for the FlowFile attribute.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor PREDICATE_STATIC = new PropertyDescriptor
    .Builder().name("Predicate Static")
    .description("If there is no FlowFile attribute, use static value.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor OBJECT_ATTRIBUTE = new PropertyDescriptor
    .Builder().name("Object Attribute")
    .description("Look for the FlowFile attribute.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor OBJECT_STATIC = new PropertyDescriptor
    .Builder().name("Object Static")
    .description("If there is no FlowFile attribute, use static value.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor IS_ADDIFYING = new PropertyDescriptor
      .Builder().name("Is Addifying?")
      .description("If this attribute is set, the object or subject entity will be " +
          "created if it does not already exist. (leave this field empty to disallow " +
          "this behaviour)")
      .required(false)
      .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
      .build();

  public static final PropertyDescriptor IS_UPDATING = new PropertyDescriptor
      .Builder().name("Updating")
      .description("Optional, default true. If is true it will check if the " +
          "property already exists, and only update the value if this new " +
          "value is new. If is false, create new property regardless.")
      .required(false)
      .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
      .build();

  @Override
  protected void init(final ProcessorInitializationContext context) {

    super.init(context);

    descriptors.add(SUBJECT_ATTRIBUTE);
    descriptors.add(SUBJECT_STATIC);
    descriptors.add(PREDICATE_ATTRIBUTE);
    descriptors.add(PREDICATE_STATIC);
    descriptors.add(OBJECT_ATTRIBUTE);
    descriptors.add(OBJECT_STATIC);
    descriptors.add(IS_ADDIFYING);
    descriptors.add(IS_UPDATING);
    this.properties = Collections.unmodifiableList(descriptors);


    this.relationships = new AtomicReference<>(relationshipSet);
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
    final ProcessorLog log = this.getLogger();

    super.onTrigger(context, session);
    Weaver weaver = getWeaver();

    FlowFile flowFile = session.get();
    if (flowFile == null) {
      throw new RuntimeException("FlowFile is null");
    }

    String id = idFromOptions(context, flowFile, true);
    String source = getSource(context, flowFile);
    
    String subjectId = valueFromOptions(context, flowFile, SUBJECT_ATTRIBUTE, SUBJECT_STATIC, null);
    String predicate = valueFromOptions(context, flowFile, PREDICATE_ATTRIBUTE, PREDICATE_STATIC, null);
    String objectId = valueFromOptions(context, flowFile, OBJECT_ATTRIBUTE, OBJECT_STATIC, null);

    // Should we be prepared for the possibility that this entity has already been created.
    boolean isAddifying = !context.getProperty(IS_ADDIFYING).isSet() || context.getProperty(IS_ADDIFYING).asBoolean();
    boolean isUpdating = !context.getProperty(IS_UPDATING).isSet() || context.getProperty(IS_UPDATING).asBoolean();

    // Create without checking for entities prior existence
    if(!isAddifying) {

      // Get the parent object from weaver
      Entity subjectEntity = weaver.get(subjectId, new ReadPayload.Opts(1));

      // Find the object
      Entity objectEntity = weaver.get(objectId, new ReadPayload.Opts(0));

      Map<String, String> entityAttributes = new HashMap<>();
      entityAttributes.put("predicate", predicate);
      entityAttributes.put("source", source);

      Map<String, ShallowEntity> relations = new HashMap<>();
      relations.put("subject", subjectEntity.toShallowEntity());
      relations.put("object", objectEntity.toShallowEntity());

      Entity individualProperty = weaver.add(entityAttributes, EntityType.INDIVIDUAL_PROPERTY, id, relations);

      // Fetch parent collection
      ShallowEntity shallowCollection = subjectEntity.getRelations().get("properties");
      Entity entityProperties = weaver.get(shallowCollection.getId(), new ReadPayload.Opts(0));

      // Link individual to collection
      entityProperties.linkEntity(individualProperty.getId(), individualProperty.toShallowEntity());


    
    } else {

      Entity datasetObjects = getDatasetObjects();

      // Get the parent object from weaver
      Entity subjectEntity;
      try {
        subjectEntity = weaver.get(subjectId);
      } catch (EntityNotFoundException e) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("source", source);
        subjectEntity = createIndividual(subjectId, attributes);
        datasetObjects.linkEntity(id, subjectEntity.toShallowEntity());
      }

      // Find the object
      Entity objectEntity;
      try {
        objectEntity = weaver.get(objectId, new ReadPayload.Opts(0));
      } catch (EntityNotFoundException e) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("source", source);
        objectEntity = createIndividual(objectId, attributes);
        datasetObjects.linkEntity(id, objectEntity.toShallowEntity());
      }

      Map<String, String> entityAttributes = new HashMap<>();
      entityAttributes.put("predicate", predicate);
      entityAttributes.put("source", source);

      Map<String, ShallowEntity> relations = new HashMap<>();
      relations.put("subject", subjectEntity.toShallowEntity());
      relations.put("object", objectEntity.toShallowEntity());

      Entity individualProperty = weaver.add(entityAttributes, EntityType.INDIVIDUAL_PROPERTY, id, relations);

      // Fetch parent collection
      ShallowEntity shallowCollection = subjectEntity.getRelations().get("properties");
      Entity entityProperties = weaver.get(shallowCollection.getId(), new ReadPayload.Opts(0));

      // Link individual to collection
      entityProperties.linkEntity(individualProperty.getId(), individualProperty.toShallowEntity());
    }

    if (context.getProperty(ATTRIBUTE_NAME_FOR_ID).isSet()) {
      String attributeNameForId = context.getProperty(ATTRIBUTE_NAME_FOR_ID).getValue();
      flowFile = session.putAttribute(flowFile, attributeNameForId, id);
    }
    session.transfer(flowFile, ORIGINAL);

  }

  @Override
  protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {

    return new PropertyDescriptor.Builder()
      .required(false)
      .name(propertyDescriptorName)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .dynamic(true)
      .expressionLanguageSupported(false)
      .build();
  }

  private Entity createIndividual(String id, Map<String, String> attributes) {
    Weaver weaver = getWeaver();

    Entity individual = weaver.add(attributes, EntityType.INDIVIDUAL, id);

    Entity entityProperties = weaver.collection();
    individual.linkEntity("properties", entityProperties.toShallowEntity());

    Entity entityAnnotations = weaver.collection();
    individual.linkEntity("annotations", entityAnnotations.toShallowEntity());

    return individual;
  }
}