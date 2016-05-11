/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.weaverplatform.nifi;

import com.weaverplatform.sdk.Entity;
import com.weaverplatform.sdk.EntityType;
import com.weaverplatform.sdk.RelationKeys;
import com.weaverplatform.sdk.Weaver;
import org.apache.nifi.annotation.behavior.*;
import org.apache.nifi.components.ConfigurableComponent;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Tags({"create, valueproperty, weaver"})
@CapabilityDescription("Creates a valueproperty object")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
@DynamicProperty(name = "Relationship Name", value = "Attribute Expression Language", supportsExpressionLanguage = false, description = "blabla")
public class CreateValueProperty extends AbstractProcessor implements ConfigurableComponent {

    public static final PropertyDescriptor WEAVER = new PropertyDescriptor
            .Builder().name("weaver_url")
            .description("weaver connection url i.e. weaver.connect(weaver_url)")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

  public static final Relationship ORIGINAL = new Relationship.Builder()
    .name("original")
    .description("Original relationship to transfer content to")
    .build();

  private List<PropertyDescriptor> properties;

  private AtomicReference<Set<Relationship>> relationships = new AtomicReference<>();

  //seperate lists for dynamic properties
  private volatile Set<String> dynamicPropertyNames = new HashSet<>();
  private Map<PropertyDescriptor, PropertyValue> propertyMap = new HashMap<>();

  @Override
  protected void init(final ProcessorInitializationContext context) {
      final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
      descriptors.add(WEAVER);
      this.properties = Collections.unmodifiableList(descriptors);

      final Set<Relationship> set = new HashSet<Relationship>();
      set.add(ORIGINAL);
      this.relationships = new AtomicReference<>(set);
  }

  /* method required for dynamic property */
  @Override
  protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
    //position 1
    return new PropertyDescriptor.Builder()
      .required(false)
      .name(propertyDescriptorName)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .dynamic(true)
      .expressionLanguageSupported(false)
      .build();
  }

  /* method from interface ConfigurableComponent */
  /*method required for dynamic property */
  @Override
  public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue){

    //position 2

    if(descriptor.isDynamic()){


      //------------first we make dynamic properties
      //System.out.println("@onPropertyModified:: dynamic prop");

      final Set<String> newDynamicPropertyNames = new HashSet<>(dynamicPropertyNames);

      if (oldValue == null) {    // new property
        //System.out.println("@onPropertyModified::oldValue=NULL");
        //newDynamicPropertyNames.addAll(this.dynamicPropertyNames);
        newDynamicPropertyNames.add(descriptor.getName());
        //dynamicPropertyValues.put(descriptor, newValue);
      }

      //TODO: what are we going to do with changed values from dynamic attributes?

      this.dynamicPropertyNames = Collections.unmodifiableSet(newDynamicPropertyNames);

    }

  }

  /* method required for dynamic property */
  @OnScheduled
  public void onScheduled(final ProcessContext context) {
    //position 3

    //System.out.println("@onScheduled");

    final Map<PropertyDescriptor, PropertyValue> newPropertyMap = new HashMap<>();
    for (final PropertyDescriptor descriptor : context.getProperties().keySet()) {
      if (!descriptor.isDynamic()) {
        continue;
      }
      //getLogger().debug("Adding new dynamic property: {}", new Object[]{descriptor});
      //System.out.println("Adding new dynamic property: {}" + descriptor.toString());
      newPropertyMap.put(descriptor, context.getProperty(descriptor));
    }

    this.propertyMap = newPropertyMap;
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

    FlowFile flowFile = session.get();

    if ( flowFile == null ) {
      //System.out.println("no flowfile");
        return;
    }

    //System.out.println("ik kom hier!");

    //get flow file attribute id
    //get flow file attribute name
    //get static attribute predicate

    final Map<PropertyDescriptor, PropertyValue> propMap = this.propertyMap;

    AtomicReference<String> subj = new AtomicReference<>();
    AtomicReference<String> pred = new AtomicReference<>();
    AtomicReference<String> obj = new AtomicReference<>();
    boolean subjStored = false;
    boolean predStored = false;
    boolean objStored = false;

    //first, look for the dynamic specified subject (id)
    for(final Map.Entry<PropertyDescriptor, PropertyValue> entry : propMap.entrySet()){

      if(entry.getKey().getName().equals("subject-attribute")) {
        //get the attribute specified by the user
        PropertyValue pv = entry.getValue();
        String subject = pv.getValue();

        if (subject != null) {
          //get the dynamic attribute from the flowfile
          String ffav = flowFile.getAttribute(subject);
          if(ffav != null) {
            //save it
            subj.set(ffav);
            subjStored = true;
            //System.out.println("subj stored");
          }
        }

      }
    }

    //second, get predicate (dynamic, but not from flowfile)
    for(final Map.Entry<PropertyDescriptor, PropertyValue> entry : propMap.entrySet()){

      if(entry.getKey().getName().equals("predicate-static")) {
        //get the attribute specified by the user
        PropertyValue pv = entry.getValue();
        String predicate = pv.getValue();

        if (predicate != null) {
          //store only
          pred.set(predicate);
          predStored = true;
          //System.out.println("pred stored");
        }

      }
    }

    //third, look for the object and get it from the flowfile too
    for(final Map.Entry<PropertyDescriptor, PropertyValue> entry : propMap.entrySet()){

      if(entry.getKey().getName().equals("value-attribute")) {
        //get the attribute specified by the user
        PropertyValue pv = entry.getValue();
        String object = pv.getValue();

        if (object != null) {
          //get its attribute from the flowfile
          String ffav = flowFile.getAttribute(object);
          if(ffav != null){
            //save it
            obj.set(ffav);
            objStored = true;
            //System.out.println("obj stored");
          }
        }

      }
    }

    //now we can weaver!
    if(subjStored && predStored && objStored) {

      String theSubject = subj.get();
      String thePredicate = pred.get();
      String theObject = obj.get();

      Weaver weaver = new Weaver();
      String weaverUrl = context.getProperty(WEAVER).getValue();
      weaver.connect(weaverUrl);

      try {

        Entity parent = weaver.get(theSubject);

        //value of object
        //create an entity attributes-list
        Map<String, Object> entityAttributes = new HashMap<>();
        entityAttributes.put("predicate", thePredicate);
        entityAttributes.put("object", theObject);

        //System.out.println("....attributes { " + firstAttributeKey + " : " + firstAttributeValue + ", " + predicateKey + " : " + predicateValue + "}");

        //object
        Entity child = weaver.add(entityAttributes, EntityType.VALUE_PROPERTY, weaver.createRandomUUID());
        //we link the parents as subject
        child.linkEntity(RelationKeys.SUBJECT, parent);

        //get the collection object from parent
        Entity aCollection = parent.getRelations().get(RelationKeys.PROPERTIES);

        //predicate
        aCollection.linkEntity(child.getId(), child);

      }catch (IndexOutOfBoundsException e) {
        System.out.println("de node waar naar gezocht moet worden is niet gevonden!");
      }

    }

    session.transfer(flowFile, ORIGINAL);


  }

  @Override
  public Set<Relationship> getRelationships() {
    return this.relationships.get();
  }

  @Override
  public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return properties;
  }

}
