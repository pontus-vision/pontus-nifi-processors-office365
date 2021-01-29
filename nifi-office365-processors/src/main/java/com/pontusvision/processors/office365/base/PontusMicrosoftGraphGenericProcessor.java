package com.pontusvision.processors.office365.base;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.graph.http.CustomRequest;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.options.FunctionOption;
import com.microsoft.graph.options.QueryOption;
import com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

@Tags({ "GRAPH", "Generic", "Microsoft", "Office 365" }) @CapabilityDescription("Get messages")

public class PontusMicrosoftGraphGenericProcessor extends PontusMicrosoftGraphBaseProcessor
{

  private List<PropertyDescriptor> properties;
  private Set<Relationship>        relationships;

  private PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;

  final static PropertyDescriptor URL = new PropertyDescriptor
    .Builder()
    .name("URL suffix")
    .defaultValue("")
    .description("The microsoft graph URL suffix (after the https://graph.microsoft.com/v1.0 prefix)")
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).required(true)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .build();

  final static PropertyDescriptor METHOD_TYPE = new PropertyDescriptor
    .Builder()
    .name("Method Type")
    .defaultValue("GET")
    .description("The microsoft graph API HTTP Method")
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .required(true)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .allowableValues("GET","PUT","POST","DELETE","PATCH")
    .build();

  final static PropertyDescriptor DATA = new PropertyDescriptor
    .Builder()
    .name("DATA")
    .defaultValue("")
    .description("The data (if required) to be sent to the API; ignored if not present")
    .addValidator(StandardValidators.createDataSizeBoundsValidator(0,1000000000))
    .required(false)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .build();

  final static PropertyDescriptor SEARCH_FIELDS = new PropertyDescriptor
    .Builder()
    .name("Search Fields").defaultValue(
        "id,createdDateTime,lastModifiedDateTime,changeKey,categories,receivedDateTime,sentDateTime,hasAttachments,internetMessageId,subject,bodyPreview,importance,parentFolderId,conversationId,isDeliveryReceiptRequested,isReadReceiptRequested,isRead,isDraft,webLink,inferenceClassification,body,sender,from,toRecipients,ccRecipients,bccRecipients,replyTo,flag")
    .description("Optional Fields to return from the Office 365 Graph API Calls."
        + "If left blank, this will return all fields.  Examples: subject,body.content,sender,from,"
        + "toRecipients,ccRecipients")
    .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
    .required(true)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .build();

  final static Validator jsonValidator =  (subject, input, context) -> {
    ValidationResult.Builder builder = new ValidationResult.Builder();
    builder.subject(subject).input(input);
    if (context.isExpressionLanguageSupported(subject) && context.isExpressionLanguagePresent(input)) {
      return builder.valid(true).explanation("Contains Expression Language").build();
    } else {
      try {
        if (!StringUtils.isBlank(input)) {
          JsonParser parser = new JsonParser();
          parser.parse(input);
        }
        builder.valid(true);
      } catch (Exception var6) {
        builder.valid(false).explanation(var6.getMessage());
      }
      return builder.build();
    }
  };

  final static PropertyDescriptor FUNCTION_OPTIONS = new PropertyDescriptor
    .Builder()
    .name("Function Options")
    .defaultValue("")
    .description("Optional Function Options; ignored if not present")
    .addValidator(jsonValidator)
    .required(false)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .build();

  final static PropertyDescriptor QUERY_OPTIONS = new PropertyDescriptor
    .Builder()
    .name("Query Options")
    .defaultValue("")
    .description("Optional Query Options; ignored if not present")
    .addValidator(jsonValidator)
    .required(false)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .build();

  final static PropertyDescriptor HEADERS = new PropertyDescriptor
    .Builder()
    .name("Headers")
    .defaultValue("")
    .description("Optional Headers; ignored if not present")
    .addValidator(jsonValidator)
    .required(false)
    .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .build();


  final static PropertyDescriptor SERVICE = new PropertyDescriptor
    .Builder()
    .name("Auththentication Controller Service")
    .displayName("Authentication Controller Service")
    .description("Controller Service to authenticate with Office 365 using oauth2")
    .required(true)
    .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
    .build();


  public static final Relationship SUCCESS =
      new Relationship.Builder()
          .name("success")
          .description("Success relationship for graph API")
          .build();

  public static final Relationship FAILURE =
      new Relationship.Builder()
          .name("failure")
          .description("Failure relationship")
          .build();


  @Override
  protected PropertyDescriptor getRegexPropertyDescriptor() {
    return null;
  }

  @Override public void init(final ProcessorInitializationContext context)
  {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(SERVICE);
    properties.add(URL);
    properties.add(METHOD_TYPE);
    properties.add(DATA);
    properties.add(SEARCH_FIELDS);
    properties.add(FUNCTION_OPTIONS);
    properties.add(QUERY_OPTIONS);
    properties.add(HEADERS);

    this.properties = Collections.unmodifiableList(properties);

    Set<Relationship> relationships = new HashSet<>();
    relationships.add(FAILURE);
    relationships.add(SUCCESS);
    this.relationships = Collections.unmodifiableSet(relationships);
  }


  public void processData (FlowFile flowFile, ProcessContext context, ProcessSession session, String data){
    JsonParser parser = new JsonParser();

    final String url = context.getProperty(URL).evaluateAttributeExpressions(flowFile).getValue();
    final String methodType = context.getProperty(METHOD_TYPE).evaluateAttributeExpressions(flowFile).getValue();
    session.remove(flowFile);
    final IGraphServiceClient graphClient = authProviderService.getService();

    final CustomRequest<JsonObject> req = graphClient.customRequest(url).buildRequest();

    final String selectStr = context.getProperty(SEARCH_FIELDS).evaluateAttributeExpressions(flowFile).getValue();

    if (StringUtils.isNotBlank(selectStr)){
      req.select(selectStr);
    }

    final String functionOptionsStr = context
        .getProperty(FUNCTION_OPTIONS)
        .evaluateAttributeExpressions(flowFile)
        .getValue();

    JsonObject funcOptions = parser.parse(functionOptionsStr).getAsJsonObject();

    funcOptions.entrySet()
        .forEach(stringJsonElementEntry -> req.addFunctionOption(new FunctionOption(stringJsonElementEntry.getKey(),
            stringJsonElementEntry.getValue().getAsString()))
    );

    final String queryOptionsStr = context
        .getProperty(QUERY_OPTIONS)
        .evaluateAttributeExpressions(flowFile)
        .getValue();

    JsonObject queryOptions = parser.parse(queryOptionsStr).getAsJsonObject();

    queryOptions.entrySet()
        .forEach(stringJsonElementEntry -> req.addQueryOption(new QueryOption(stringJsonElementEntry.getKey(),
            stringJsonElementEntry.getValue().getAsString()))
        );

    final String headersStr = context
        .getProperty(HEADERS)
        .evaluateAttributeExpressions(flowFile)
        .getValue();

    JsonObject headers = parser.parse(headersStr).getAsJsonObject();

    headers.entrySet()
        .forEach(stringJsonElementEntry -> req.addHeader(stringJsonElementEntry.getKey(),
            stringJsonElementEntry.getValue().getAsString())
        );

    JsonObject obj = null;
    if("GET".equalsIgnoreCase(methodType)){
      obj = req.get();
    }
    else if("POST".equalsIgnoreCase(methodType)){
      JsonObject postObj = parser.parse(data).getAsJsonObject();
      obj = req.post(postObj);
    }
    else if("PUT".equalsIgnoreCase(methodType)){
      JsonObject postObj = parser.parse(data).getAsJsonObject();
      obj = req.put(postObj);
    }
    else if("PATCH".equalsIgnoreCase(methodType)){
      JsonObject postObj = parser.parse(data).getAsJsonObject();
      obj = req.patch(postObj);
    }
    else if("DELETE".equalsIgnoreCase(methodType)){
      req.delete();
    }

    if (obj != null){
      writeFlowFile(flowFile,session,obj.getAsString(),SUCCESS );
    }


    //      session.transfer(flowFile, ORIGINAL);
  }

  @Override public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    FlowFile flowFile = session.get();

    if (flowFile == null)
    {
      return;
    }
    String data = null;
    try
    {
      data = context.getProperty(DATA).evaluateAttributeExpressions(flowFile).getValue();

      if (StringUtils.isBlank(data))
      {
        data = readFromFlowFile(session,flowFile);
  //      if (StringUtils.isBlank(data)) {
  //        getLogger().error("Unable to process flow File; must add the attribute data");
  //        session.transfer(flowFile, FAILURE);
  //        return;
  //      }
      }

      if (authProviderService == null)
      {
        authProviderService = context
          .getProperty(SERVICE)
          .asControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class);
      }


      processData(flowFile,context,session,data);
    }
    catch (Exception e)
    {
      try
      {
        authProviderService.refreshToken();
        processData(flowFile,context,session,data);
      }
      catch (Exception ex2)
      {
        PontusMicrosoftGraphBaseProcessor.handleError(getLogger(), ex2, session);
      }
    }

  }

  private String readFromFlowFile(ProcessSession session, FlowFile flowFile) {
    final StringBuilder sb = new StringBuilder();
    session.read(flowFile, in -> {
      try
      {
        String queryStr = IOUtils.toString(in, Charset.defaultCharset());
        sb.append(queryStr);
      }
      catch (Exception e)
      {
        getLogger().error("Failed to run query against Tinkerpop server; error: {}", e);
        throw new IOException(e);
      }
    });
    return sb.toString();
  }

  @Override public Set<Relationship> getRelationships()
  {
    return relationships;
  }

  @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors()
  {
    return properties;
  }
}
