package com.weaverplatform.nifi.individual;

import com.weaverplatform.sdk.Entity;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
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

@Tags({"weaver, create, entity"})
@CapabilityDescription("Create entity")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class WipeProcessor extends FlowFileProcessor {
  
  public static final PropertyDescriptor ENTITY_TYPE = new PropertyDescriptor
    .Builder().name("Entity Type")
    .description("The created entity will be of this type (e.g. $INDIVIDUAL).")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();
  
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
    .Builder().name("Name Prefix")
    .description("If this is set all names are prefixed with this string (add a trailing space yourself).")
    .required(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

  @Override
  protected void init(final ProcessorInitializationContext context) {
    
    super.init(context); 
    
    
    descriptors.add(ENTITY_TYPE);
    descriptors.add(NAME_ATTRIBUTE);
    descriptors.add(NAME_STATIC);
    descriptors.add(NAME_PREFIX);
    this.properties = Collections.unmodifiableList(descriptors);


    this.relationships = new AtomicReference<>(relationshipSet);
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
    final ProcessorLog log = this.getLogger();

    super.onTrigger(context, session);

    String id = idFromOptions(context, flowFile, true);

    // Create entity by user attribute
    Map<String, Object> attributes = new HashMap<>();
    String name = valueFromOptions(context, flowFile, NAME_ATTRIBUTE, NAME_STATIC, "Unnamed");

    // Check for prefix
    if(context.getProperty(NAME_PREFIX).isSet()) {
      name += context.getProperty(NAME_PREFIX).getValue();
    }
    attributes.put("name", name);

    
    String entityType = context.getProperty(ENTITY_TYPE).getValue();

    log.info("create entity of type "+entityType+" with id "+id+" with name " + name);

    Entity individual = weaver.add(attributes, entityType, id);

    weaver.close();

    if(context.getProperty(ATTRIBUTE_NAME_FOR_ID).isSet()) {
      String attributeNameForId = context.getProperty(ATTRIBUTE_NAME_FOR_ID).getValue();
      flowFile = session.putAttribute(flowFile, attributeNameForId, id);
    }
    session.transfer(flowFile, ORIGINAL);
  }
}
