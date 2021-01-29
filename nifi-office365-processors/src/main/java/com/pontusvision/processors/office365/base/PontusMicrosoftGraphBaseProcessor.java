package com.pontusvision.processors.office365.base;

import com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.distributed.cache.client.Deserializer;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

import static com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface.getStackTrace;

@Tags({ "GRAPH", "User", "Microsoft", "Office 365" }) @CapabilityDescription(
    "Gets Office users, and adds the userID for each user"
        + " in the office365_user_id flow file attribute")
@DynamicProperty(name = "Generated FlowFile attribute name", value = "Generated FlowFile attribute value",
    expressionLanguageScope = ExpressionLanguageScope.VARIABLE_REGISTRY,
    description = "Specifies an attribute on generated FlowFiles defined by the Dynamic Property's key and value." +
        " If Expression Language is used, evaluation will be performed only once per batch of generated FlowFiles.")
abstract public class PontusMicrosoftGraphBaseProcessor extends AbstractProcessor
{
  public static final String OFFICE365_USER_ID     = "office365_user_id";
  public static final String OFFICE365_FOLDER_ID   = "office365_folder_id";
  public static final String OFFICE365_MESSAGE_ID  = "office365_message_id";
  public static final String OFFICE365_DELTA_VALUE = "office365_delta";
  public static final String OFFICE365_DELTA_KEY   = "office365_delta_key";
  public static final String OFFICE365_CACHE_KEY   = "office365_cache_key";

  public static final String OFFICE365_DELTA_KEY_FORMAT_MESSAGE_PREFIX = "O365_messages";
  public static final String OFFICE365_DELTA_KEY_FORMAT_USER_PREFIX    = "O365_users_delta";
  public static final String OFFICE365_DELTA_KEY_FORMAT_FOLDER_PREFIX  = "O365_folders";

  public static final String OFFICE365_DELTA_KEY_FORMAT_MESSAGE = OFFICE365_DELTA_KEY_FORMAT_MESSAGE_PREFIX + "|%s|%s";
  public static final String OFFICE365_DELTA_KEY_FORMAT_USER    = OFFICE365_DELTA_KEY_FORMAT_USER_PREFIX;
  public static final String OFFICE365_DELTA_KEY_FORMAT_FOLDER  = OFFICE365_DELTA_KEY_FORMAT_FOLDER_PREFIX + "|%s";

  public static final String OFFICE365_REGEX_MESSAGE_DEFAULT = OFFICE365_DELTA_KEY_FORMAT_MESSAGE_PREFIX + ".*";
  public static final String OFFICE365_REGEX_USER_DEFAULT    = OFFICE365_DELTA_KEY_FORMAT_USER_PREFIX;
  public static final String OFFICE365_REGEX_FOLDER_DEFAULT  = OFFICE365_DELTA_KEY_FORMAT_FOLDER_PREFIX + ".*";

  protected List<PropertyDescriptor> properties;
  protected Set<Relationship>        relationships;

  protected DistributedMapCacheClient                          cacheClient         = null;
  protected String                                             cacheFilterRegexStr = null;
  protected PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;
  protected Pattern                                            cacheFilterRegex;

  public static final PropertyDescriptor OFFICE365_DISTRIB_MAP_CACHE = new PropertyDescriptor
      .Builder()
      .name("Distributed Map Cache Client")
      .description("A Distributed Map Cache with ids to trigger the workflow")
      .required(true)
      .identifiesControllerService(DistributedMapCacheClient.class)
      .build();

  public final static PropertyDescriptor CACHE_FILTER_REGEX_USER = new PropertyDescriptor.Builder()
      .name("Cache Filter User Regex")
      .defaultValue(OFFICE365_REGEX_USER_DEFAULT)
      .description(
          "Cache filter - this will choose which entries in the cache that will be processed by this processor")
      .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
      .addValidator(
          (subject, input, context) -> input.contains(OFFICE365_DELTA_KEY_FORMAT_USER_PREFIX) ?
              Validator.VALID.validate(subject, input, context) :
              new ValidationResult
                  .Builder()
                  .valid(false)
                  .subject(subject)
                  .input(input)
                  .explanation("The regex must contain the string " + OFFICE365_DELTA_KEY_FORMAT_USER_PREFIX)
                  .build())
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .required(true)
      .build();

  public final static PropertyDescriptor CACHE_FILTER_REGEX_MESSAGE = new PropertyDescriptor.Builder()
      .name("Cache Filter Message Regex")
      .defaultValue(OFFICE365_REGEX_MESSAGE_DEFAULT)
      .description(
          "Cache filter - this will choose which entries in the cache that will be processed by this processor")
      .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
      .addValidator(
          (subject, input, context) -> input.contains(OFFICE365_DELTA_KEY_FORMAT_MESSAGE_PREFIX) ?
              Validator.VALID.validate(subject, input, context) :
              new ValidationResult
                  .Builder()
                  .valid(false)
                  .subject(subject)
                  .input(input)
                  .explanation("The regex must contain the string " + OFFICE365_DELTA_KEY_FORMAT_MESSAGE_PREFIX)
                  .build())
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .required(true)
      .build();

  public final static PropertyDescriptor CACHE_FILTER_REGEX_FOLDER = new PropertyDescriptor.Builder()
      .name("Cache Filter Folder Regex")
      .defaultValue(OFFICE365_REGEX_FOLDER_DEFAULT)
      .description(
          "Cache filter - this will choose which entries in the cache that will be processed by this processor")
      .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
      .addValidator(
          (subject, input, context) -> input.contains(OFFICE365_DELTA_KEY_FORMAT_FOLDER_PREFIX) ?
              Validator.VALID.validate(subject, input, context) :
              new ValidationResult
                  .Builder()
                  .valid(false)
                  .subject(subject)
                  .input(input)
                  .explanation("The regex must contain the string " + OFFICE365_DELTA_KEY_FORMAT_FOLDER_PREFIX)
                  .build())
      .required(true)
      .build();

  public final static PropertyDescriptor SERVICE = new PropertyDescriptor.Builder()
      .name("Controller Service")
      .displayName("Controller Service")
      .description("Authentication Controller Service")
      .required(true)
      .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
      .build();

  public static final Relationship ORIGINAL = new Relationship.Builder().name("Original")
                                                                        .description("Success relationship").build();

  public static final Relationship SUCCESS = new Relationship.Builder().name("Success")
                                                                       .description("Success relationship").build();

  public static final Relationship FAILURE = new Relationship.Builder().name("Failure")
                                                                       .description("Failure relationship").build();

  public static Serializer<String>   SER = (s, outputStream) -> outputStream.write(s.getBytes());
  public static Deserializer<String> DES = String::new;

  protected abstract PropertyDescriptor getRegexPropertyDescriptor();

  @Override public void init(final ProcessorInitializationContext context)
  {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(SERVICE);
    properties.add(OFFICE365_DISTRIB_MAP_CACHE);
    properties.add(getRegexPropertyDescriptor());

    this.properties = Collections.unmodifiableList(properties);

    Set<Relationship> relationships = new HashSet<>();
    relationships.add(ORIGINAL);
    relationships.add(SUCCESS);
    relationships.add(FAILURE);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  public static void writeFlowFile(FlowFile flowFile, ProcessSession session, String data, Relationship relationship)
  {
    FlowFile ff = flowFile;
    ff = session.write(ff, out -> IOUtils.write(data, out, Charset.defaultCharset()));
    session.transfer(ff, relationship);
  }

  @OnScheduled
  public void onScheduled(final ProcessContext context)
  {
    cacheFilterRegexStr = context.getProperty(getRegexPropertyDescriptor()).evaluateAttributeExpressions().getValue();
    cacheFilterRegex = Pattern.compile(cacheFilterRegexStr);

    if (authProviderService == null)
    {
      authProviderService = context.getProperty(SERVICE)
                                   .asControllerService(
                                       PontusMicrosoftGraphAuthControllerServiceInterface.class);
    }

    if (cacheClient == null)
    {
      cacheClient = context.getProperty(OFFICE365_DISTRIB_MAP_CACHE)
                           .asControllerService(
                               DistributedMapCacheClient.class);
    }
  }

  @Override public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    FlowFile flowFile = session.get();
    if (flowFile == null)
    {
      flowFile = session.create();
    }

    try
    {
      Set<String> keys = cacheClient.keySet(DES);

      long counter = 0;

      for (String key : keys)
      {
        if (cacheFilterRegex.matcher(key).matches())
        {
          counter++;
          process(context, session, flowFile, key, cacheClient.get(key, SER, DES));
        }
      }
      if (counter == 0)
      {
        process(context, session, flowFile);
      }

      session.transfer(flowFile, ORIGINAL);
    }
    catch (Exception ex)
    {
      session.remove(flowFile);
      handleError(getLogger(), ex, session);
    }
  }

  @OnStopped
  public void onStopped()
  {
    cacheClient = null;
    authProviderService = null;
  }

  public void process(ProcessContext context, ProcessSession session, FlowFile flowFile, String key,
                      String delta) throws Exception
  {

  }

  public void process(ProcessContext context, ProcessSession session, FlowFile flowFile) throws Exception
  {

  }

  @Override public Set<Relationship> getRelationships()
  {
    return relationships;
  }

  @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors()
  {
    return properties;
  }

  public static void handleError(ComponentLog logger, Exception ex, ProcessSession session)
  {
    logger.error("Unable to process", ex);

    FlowFile flowFile = session.create();
    flowFile = session.putAttribute(flowFile, "Office365.Error", ex.getMessage());
    flowFile = session.putAttribute(flowFile, "Office365.StackTrace", getStackTrace(ex));
    session.transfer(flowFile, FAILURE);

  }

}
