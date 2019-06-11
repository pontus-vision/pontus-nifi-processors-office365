package com.pontusvision.processors.office365;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.*;
import com.microsoft.graph.models.generated.BodyType;
import com.microsoft.graph.models.generated.Importance;
import com.microsoft.graph.requests.extensions.IUserRequestBuilder;
import com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.nio.charset.Charset;
import java.util.List;
import java.util.*;

@Tags({ "GRAPH", "Message", "Microsoft", "Office 365", "mail",
    "email" , "send", "o365"}) @CapabilityDescription("Sends Office 365 email messages")

public class PontusMicrosoftGraphMessageSendProcessor extends AbstractProcessor
{

  private List<PropertyDescriptor> properties;
  private Set<Relationship>        relationships;

  public final static String                                             SERVICE_NAME        = "Auththentication Controller Service";
  public final static PropertyDescriptor                                 SERVICE             = new PropertyDescriptor.Builder()
      .name(SERVICE_NAME).displayName(SERVICE_NAME)
      .description("Controller Service to authenticate with Office 365 using oauth2").required(true)
      .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
      .build();
  private             PontusMicrosoftGraphAuthControllerServiceInterface authProviderService = null;

  public final static String             SUBJECT_NAME    = "Email Subject";
  public final static String             SUBJECT_DEFAULT = "Email Subject";
  final static        PropertyDescriptor SUBJECT         = new PropertyDescriptor.Builder()
      .name(SUBJECT_NAME).displayName(SUBJECT_NAME)
      .description("Subject of an e-mail message").required(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .defaultValue(SUBJECT_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  public final static String             BODY_TYPE_DEFAULT = "HTML";
  public final static String             BODY_TYPE_NAME    = "Email body type Type";
  public final static PropertyDescriptor BODY_TYPE         = new PropertyDescriptor.Builder()
      .name(BODY_TYPE_NAME).displayName(BODY_TYPE_NAME)
      .description("Body type of an e-mail message (HTML or Text)").required(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .defaultValue(BODY_TYPE_DEFAULT)
      .allowableValues("HTML", "Text")
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();



  public final static String             BODY_DEFAULT = "";
  public final static String             BODY_NAME    = "Email Body";
  public final static PropertyDescriptor BODY         = new PropertyDescriptor.Builder()
      .name(BODY_NAME).displayName(BODY_NAME)
      .description("Plain Text or HTML Body of an e-mail message.  If this is an empty string, "
          + "the body will be read from the flow file's body.").required(true)
      .addValidator(Validator.VALID)
      .defaultValue(BODY_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();


  public final static String             USER_ID_DEFAULT = "me";
  public final static String             USER_ID_NAME    = "User ID";
  public final static PropertyDescriptor USER_ID         = new PropertyDescriptor.Builder()
      .name(USER_ID_NAME).displayName(USER_ID_NAME)
      .description("A GUID that represents an office 365 userID, or the word 'me'; if set to me, the message will be "
          + "sent from the user that has set the credentials for this service").required(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .defaultValue(USER_ID_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  public final static String             SAVE_TO_SENT_ITEMS_DEFAULT = "true";
  public final static String             SAVE_TO_SENT_ITEMS_NAME    = "Save To Sent Items";
  public final static PropertyDescriptor SAVE_TO_SENT_ITEMS         = new PropertyDescriptor.Builder()
      .name(SAVE_TO_SENT_ITEMS_NAME).displayName(SAVE_TO_SENT_ITEMS_NAME)
      .description("This controls whether a message is stored in the user's sent items message folder.").required(true)
      .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
      .defaultValue(SAVE_TO_SENT_ITEMS_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  public final static String             TO_RECIPIENTS_DEFAULT = "";
  public final static String             TO_RECIPIENTS_NAME    = "To Email Addresses";
  public final static PropertyDescriptor TO_RECIPIENTS         = new PropertyDescriptor.Builder()
      .name(TO_RECIPIENTS_NAME).displayName(TO_RECIPIENTS_NAME)
      .description("This has a comma-separated list of e-mail 'to' recipients.").required(true)
      .addValidator(Validator.VALID)
      .defaultValue(TO_RECIPIENTS_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();


  public final static String             CC_RECIPIENTS_DEFAULT = "";
  public final static String             CC_RECIPIENTS_NAME    = "CC Email Addresses";
  public final static PropertyDescriptor CC_RECIPIENTS         = new PropertyDescriptor.Builder()
      .name(CC_RECIPIENTS_NAME).displayName(CC_RECIPIENTS_NAME)
      .description("This has a comma-separated list of e-mail 'Carbon-copy (cc)'  recipients.").required(true)
      .addValidator(Validator.VALID)
      .defaultValue(CC_RECIPIENTS_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();



  public final static String             BCC_RECIPIENTS_DEFAULT = "";
  public final static String             BCC_RECIPIENTS_NAME    = "BCC Email Addresses";
  public final static PropertyDescriptor BCC_RECIPIENTS         = new PropertyDescriptor.Builder()
      .name(BCC_RECIPIENTS_NAME).displayName(BCC_RECIPIENTS_NAME)
      .description("This has a comma-separated list of e-mail 'Blind Carbon-copy (bcc)'  recipients.").required(true)
      .addValidator(Validator.VALID)
      .defaultValue(BCC_RECIPIENTS_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();


  public final static String             IMPORTANCE_DEFAULT = "Normal";
  public final static String             IMPORTANCE_NAME    = "BCC Email Addresses";
  public final static PropertyDescriptor IMPORTANCE         = new PropertyDescriptor.Builder()
      .name(IMPORTANCE_NAME).displayName(IMPORTANCE_NAME)
      .description("This has a comma-separated list of e-mail 'Blind Carbon-copy (bcc)'  recipients.").required(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .allowableValues("Low", "Normal", "High")
      .defaultValue(IMPORTANCE_DEFAULT)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();


  public static final Relationship SUCCESS = new Relationship.Builder().name("success")
                                                                       .description("Success relationship for messages")
                                                                       .build();

  public static final Relationship FAILURE = new Relationship.Builder().name("failure")
                                                                       .description("Failure relationship").build();

  @Override public void init(final ProcessorInitializationContext context)
  {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(SERVICE);
    properties.add(USER_ID);
    properties.add(SUBJECT);
    properties.add(TO_RECIPIENTS);
    properties.add(CC_RECIPIENTS);
    properties.add(BCC_RECIPIENTS);
    properties.add(SAVE_TO_SENT_ITEMS);
    properties.add(IMPORTANCE);
    properties.add(BODY_TYPE);
    properties.add(BODY);

    this.properties = Collections.unmodifiableList(properties);

    Set<Relationship> relationships = new HashSet<>();
    relationships.add(FAILURE);
    relationships.add(SUCCESS);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  public static void writeFlowFile(FlowFile flowFile, ProcessSession session, String data, Relationship rel)
  {
    FlowFile ff = session.create(flowFile);
    ff = session.write(ff, out -> {
      IOUtils.write(data, out, Charset.defaultCharset());
    });
    session.transfer(ff, rel);
  }

  public static List<Recipient> getRecipientsList(String recipientsCSV)
  {
    if (recipientsCSV != null && recipientsCSV.length() > 3)
    {
      String[]        recipientsSplit = recipientsCSV.split(",");
      List<Recipient> bccRecipients   = new ArrayList<>(recipientsSplit.length);

      for (String bccRecipient : recipientsSplit)
      {
        Recipient rec = new Recipient();
        rec.emailAddress = new EmailAddress();
        rec.emailAddress.address = bccRecipient;
        bccRecipients.add(rec);
      }

      return bccRecipients;
    }
    return Collections.emptyList();

  }

  public static String getBody(final ProcessContext context, ProcessSession session, final FlowFile flowFile)
  {
    String body = context.getProperty(BODY).evaluateAttributeExpressions(flowFile).getValue();

    if (body == null || body.length() == 0)
    {
      final StringBuilder bodySb = new StringBuilder();
      session.read(flowFile, in -> bodySb.append(IOUtils.toString(in, Charset.defaultCharset())));
    }

    return body;
  }

  /*
   * Load Messages
   */
  public static void sendMessage(String userId, String subject, String bccRecipients, String ccRecipients,
                                 String toRecipients, String body, String bodyType, boolean saveToSentItems,
                                 String importance,
                                 IGraphServiceClient graphClient) throws ClientException
  {
    IUserRequestBuilder user = null;

    if ("me".equalsIgnoreCase(userId))
    {
      user = graphClient.me();

    }
    else
    {
      user = graphClient.users(userId);
    }

    Message msg = new Message();

    msg.body = new ItemBody();
    msg.body.content = body;
    msg.body.contentType = BodyType.valueOf(bodyType.toUpperCase());

    msg.subject = subject;

    msg.bccRecipients = getRecipientsList(bccRecipients);

    msg.ccRecipients = getRecipientsList(ccRecipients);

    msg.toRecipients = getRecipientsList(toRecipients);
    msg.importance = Importance.valueOf(importance.toUpperCase());

    user.sendMail(msg, saveToSentItems).buildRequest().post();

  }

  @Override public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue,
                                           final String newValue)
  {
    if (descriptor.equals(SERVICE))
    {
      authProviderService = null;
    }

  }

  @Override public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    FlowFile flowFile = session.get();

    if (flowFile == null)
    {
      return;
    }

    if (authProviderService == null)
    {
      authProviderService = context.getProperty(SERVICE)
                                   .asControllerService(
                                       PontusMicrosoftGraphAuthControllerServiceInterface.class);
    }
    String subject = context.getProperty(SUBJECT).evaluateAttributeExpressions(flowFile).getValue();

    String body = getBody(context,session,flowFile);
    String bodyType = context.getProperty(BODY_TYPE).evaluateAttributeExpressions(flowFile).getValue();
    String userId = context.getProperty(USER_ID).evaluateAttributeExpressions(flowFile).getValue();

    Boolean saveToSentItems = context.getProperty(SAVE_TO_SENT_ITEMS).evaluateAttributeExpressions(flowFile).asBoolean();

    String  toRecipients = context.getProperty(TO_RECIPIENTS).evaluateAttributeExpressions(flowFile).getValue();

    String ccRecipients = context.getProperty(CC_RECIPIENTS).evaluateAttributeExpressions(flowFile).getValue();

    String bccRecipients = context.getProperty(BCC_RECIPIENTS).evaluateAttributeExpressions(flowFile).getValue();

    String importance = context.getProperty(IMPORTANCE).evaluateAttributeExpressions(flowFile).getValue();

    try
    {
      sendMessage(userId, subject, bccRecipients, ccRecipients, toRecipients, body, bodyType, saveToSentItems,
          importance,
          authProviderService.getService());

      session.transfer(flowFile, SUCCESS);
    }
    catch (Exception ex)
    {
      getLogger().error("Unable to process", ex);
      session.transfer(flowFile, FAILURE);
    }

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
