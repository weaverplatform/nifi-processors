package com.weaverplatform.nifi.individual;

import com.weaverplatform.sdk.*;
import com.weaverplatform.sdk.json.request.ReadPayload;
import com.weaverplatform.sdk.json.request.UpdateEntityAttribute;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Tags({"weaver, create, individual"})
@CapabilityDescription("Create individual object")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class CreateIndividual extends FlowFileProcessor {
  
  public static final PropertyDescriptor NAME_ATTRIBUTE = new PropertyDescriptor
    .Builder().name("Name Attribute")
    .description("Look for a FlowFile attribute to set the name.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor NAME_STATIC = new PropertyDescriptor
    .Builder().name("Name Static")
    .description("Look for a FlowFile attribute to set the name.")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  public static final PropertyDescriptor NAME_PREFIX = new PropertyDescriptor
      .Builder().name("Name Static")
      .description("Look for a FlowFile attribute to set the name.")
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

  public static final PropertyDescriptor DO_NOT_CHECK_EXISTENCE = new PropertyDescriptor
    .Builder().name("Do not check existence")
    .description("")
    .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
    .required(false)
    .allowableValues(new String[]{"true", "false"})
    .defaultValue("false")
    .build();

  public static final PropertyDescriptor IS_UPDATING = new PropertyDescriptor
      .Builder().name("Updating")
      .description("Optional, default true. If is true it will check if the " +
          "property already exists, and only update the value if this new " +
          "value is new. If is false, create new property regardless.")
      .required(false)
      .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
      .build();

  private Entity individual;
  private Entity propertiesCollection;

  @Override
  protected void init(final ProcessorInitializationContext context) {
    
    super.init(context);
    
    
    descriptors.add(NAME_ATTRIBUTE);
    descriptors.add(NAME_STATIC);
    descriptors.add(IS_ADDIFYING);
    descriptors.add(IS_UPDATING);
    descriptors.add(DO_NOT_CHECK_EXISTENCE);
    this.properties = Collections.unmodifiableList(descriptors);
    this.relationships = new AtomicReference<>(relationshipSet);
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
    final ProcessorLog log = this.getLogger();
    
    Weaver weaver = getWeaver();

    FlowFile flowFile = session.get();
    if (flowFile == null) {
      return;
    }

    Entity datasetObjects = getDatasetObjects();

    String id = idFromOptions(context, flowFile, true);
    String name = getName(context, flowFile);
    String source = getSource(context, flowFile);

    if(id.equals("") || id.contains(" ") || id == null) {

      //Write flowfile to error heap, and send it through the flow without any other processing
      new FlowErrorCatcher(context, session, this.getIdentifier()).dump(flowFile);
      //log.warn("Subject ("+(subjectId.equals("") ? 'X' : "") + "), object ("+(objectId.equals("") ? 'X' : "") + "), or predicate ("+(predicate.equals("") ? 'X' : "") + ") was empty");
      session.transfer(flowFile, ORIGINAL);
      return;
    }

    // Should we be prepared for the possibility that this entity has already been created.
    boolean isAddifying = !context.getProperty(IS_ADDIFYING).isSet() || context.getProperty(IS_ADDIFYING).asBoolean();
    boolean doNotCheckExistence = context.getProperty(DO_NOT_CHECK_EXISTENCE).asBoolean();
    boolean isUpdating =  !context.getProperty(IS_UPDATING).isSet()  || context.getProperty(IS_UPDATING).asBoolean();

    // Create without checking for entities prior existence
    if(doNotCheckExistence) {

      ConcurrentMap<String, String> attributes = new ConcurrentHashMap<>();
      attributes.put("name", name);
      attributes.put("source", source);

      createIndividual(id, attributes);

      // Attach to dataset
      datasetObjects.linkEntity(id, individual.toShallowEntity());

      // Check to see whether it exists before creation
    } else {

      try {
        individual = weaver.get(id, new ReadPayload.Opts(1));
        if (!"".equals(name)) {

          // Check if name attribute is set
          if (!individual.getAttributes().containsKey("name") || !name.equals(individual.getAttributes().get("name"))) {
            weaver.updateEntityAttribute(new UpdateEntityAttribute(new ShallowEntity(individual.getId(), individual.getType()), "name", new ShallowValue(name, "")));
            weaver.updateEntityAttribute(new UpdateEntityAttribute(new ShallowEntity(individual.getId(), individual.getType()), "source", new ShallowValue(source, "")));
          }
        }
      } catch(EntityNotFoundException e) {

        ConcurrentMap<String, String> attributes = new ConcurrentHashMap<>();
        attributes.put("name", name);
        attributes.put("source", source);

        createIndividual(id, attributes);

        // Attach to dataset
        datasetObjects.linkEntity(id, individual.toShallowEntity());
      }
    }
    if(context.getProperty(ATTRIBUTE_NAME_FOR_ID).isSet()) {
      String attributeNameForId = context.getProperty(ATTRIBUTE_NAME_FOR_ID).getValue();
      flowFile = session.putAttribute(flowFile, attributeNameForId, id);
    }
    session.transfer(flowFile, ORIGINAL);
  }

  private void createIndividual(String id, ConcurrentMap<String, String> attributes) {
    Weaver weaver = getWeaver();

    individual = weaver.add(attributes, EntityType.INDIVIDUAL, id);
    propertiesCollection = weaver.collection();
    individual.linkEntity("properties", propertiesCollection.toShallowEntity());
  }

  private String getName(ProcessContext context, FlowFile flowFile) {
    String name = valueFromOptions(context, flowFile, NAME_ATTRIBUTE, NAME_STATIC, "Unnamed");

    // Check for prefix
    if(context.getProperty(NAME_PREFIX).isSet()) {
      name = context.getProperty(NAME_PREFIX).getValue() + name;
    }
    return name;
  }
}
