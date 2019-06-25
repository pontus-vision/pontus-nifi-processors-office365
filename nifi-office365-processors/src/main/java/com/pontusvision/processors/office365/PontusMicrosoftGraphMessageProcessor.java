package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.Attachment;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.Message;
import com.microsoft.graph.requests.extensions.IAttachmentCollectionPage;
import com.microsoft.graph.requests.extensions.IAttachmentCollectionRequest;
import com.microsoft.graph.requests.extensions.IMessageCollectionPage;
import com.microsoft.graph.requests.extensions.IMessageCollectionRequest;
import com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.nio.charset.Charset;
import java.util.*;

import static com.pontusvision.processors.office365.PontusMicrosoftGraphUserProcessor.OFFICE365_USER_ID;

@Tags({ "GRAPH", "Message", "Microsoft", "Office 365" }) @CapabilityDescription("Get messages")

public class PontusMicrosoftGraphMessageProcessor extends AbstractProcessor
{

  private List<PropertyDescriptor> properties;
  private Set<Relationship>        relationships;

  private String                                             messageFields = null;
  private PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;

  final static PropertyDescriptor MESSAGE_FIELDS = new PropertyDescriptor.Builder()
      .name("Message Fields").defaultValue(
          "id,createdDateTime,lastModifiedDateTime,changeKey,categories,receivedDateTime,sentDateTime,hasAttachments,internetMessageId,subject,bodyPreview,importance,parentFolderId,conversationId,isDeliveryReceiptRequested,isReadReceiptRequested,isRead,isDraft,webLink,inferenceClassification,body,sender,from,toRecipients,ccRecipients,bccRecipients,replyTo,flag")
      .description("Message Fields to return from the Office 365 Graph API for Emails.  "
          + "If left blank, this will return all fields.  Examples: subject,body.content,sender,from,"
          + "toRecipients,ccRecipients").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  final static PropertyDescriptor SERVICE = new PropertyDescriptor.Builder()
      .name("Auththentication Controller Service").displayName("Authentication Controller Service")
      .description("Controller Service to authenticate with Office 365 using oauth2").required(true)
      .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
      .build();

  public static final Relationship SUCCESS_MESSAGES = new Relationship.Builder().name("success_messages")
                                                                                .description(
                                                                                    "Success relationship for messages")
                                                                                .build();

  public static final Relationship SUCCESS_ATTACHMENTS = new Relationship.Builder().name("success_attachments")
                                                                                   .description(
                                                                                       "Success relationship for attachments")
                                                                                   .build();

  //  public static final Relationship ORIGINAL = new Relationship.Builder().name("original")
  //                                                                       .description("Failure relationship").build();

  public static final Relationship FAILURE = new Relationship.Builder().name("failure")
                                                                       .description("Failure relationship").build();

  @Override public void init(final ProcessorInitializationContext context)
  {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(SERVICE);
    properties.add(MESSAGE_FIELDS);

    this.properties = Collections.unmodifiableList(properties);

    Set<Relationship> relationships = new HashSet<>();
    relationships.add(FAILURE);
    relationships.add(SUCCESS_MESSAGES);
    relationships.add(SUCCESS_ATTACHMENTS);
    //    relationships.add(ORIGINAL);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  private void loadAttachments(String userId, Message message, IGraphServiceClient graphClient,
                               FlowFile flowFile, ProcessSession session)
  {

    IAttachmentCollectionRequest request = graphClient.users(userId).messages(message.id).attachments()
                                                      .buildRequest();
    do
    {
      IAttachmentCollectionPage page        = request.get();
      List<Attachment>          attachments = page.getCurrentPage();

      if (attachments != null && !attachments.isEmpty())
      {
        for (Attachment attachment : attachments)
        {
          writeFlowFile(flowFile, session, attachment.getRawObject().toString(), SUCCESS_ATTACHMENTS);
        }
      }

      // Get next page request
      if (page.getNextPage() != null)
      {
        request = page.getNextPage().buildRequest();
      }
      else
      {
        request = null;
      }

    } while (request != null);

  }

  public static void writeFlowFile(FlowFile flowFile, ProcessSession session, String data, Relationship rel)
  {
    FlowFile ff = session.create(flowFile);
    ff = session.write(ff, out -> {
      IOUtils.write(data, out, Charset.defaultCharset());
    });
    session.transfer(ff, rel);
  }

  /*
   * Load Messages
   */
  private void loadMessages(String userId, IGraphServiceClient graphClient,
                            Map<String, String> attribs, ProcessSession session) throws Exception
  {
    IMessageCollectionRequest request = graphClient
        .users(userId)
        .messages()
        .buildRequest().top(10)
        .select(messageFields);

    do
    {
      IMessageCollectionPage page     = request.get();
      List<Message>          messages = page.getCurrentPage();

      if (messages != null && !messages.isEmpty())
      {
        for (Message message : messages)
        {
          FlowFile flowFile = session.create();
          flowFile = session.putAllAttributes(flowFile, attribs);
          loadAttachments(userId, message, graphClient, flowFile, session);
          writeFlowFile(flowFile, session, message.getRawObject().toString(), SUCCESS_MESSAGES);
          session.remove(flowFile);
          session.commit();
        }
      }

      // Get next page request
      if (page.getNextPage() != null)
      {
        request = page.getNextPage().buildRequest();
      }
      else
      {
        request = null;
      }
    }
    while (request != null);

  }

  @Override public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue,
                                           final String newValue)
  {
    if (descriptor.equals(MESSAGE_FIELDS))
    {
      messageFields = newValue;
    }
  }

  @Override public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    FlowFile flowFile = session.get();

    if (flowFile == null)
    {
      return;
    }

    Map<String, String> attributes = flowFile.getAttributes();

    String userId = flowFile.getAttribute(OFFICE365_USER_ID);

    if (userId == null)
    {
      getLogger().error("Unable to process flow File; must add the attribute " + OFFICE365_USER_ID);
      session.transfer(flowFile, FAILURE);

      return;
    }

    if (authProviderService == null)
    {
      authProviderService = context.getProperty(SERVICE)
                                   .asControllerService(
                                       PontusMicrosoftGraphAuthControllerServiceInterface.class);
    }

    messageFields = context.getProperty(MESSAGE_FIELDS).evaluateAttributeExpressions(flowFile).getValue();

    try
    {
      session.remove(flowFile);

      loadMessages(userId, authProviderService.getService(), attributes, session);
      //      session.transfer(flowFile, ORIGINAL);
    }
    catch (Exception e)
    {
      try
      {
        authProviderService.refreshToken();
        loadMessages(userId, authProviderService.getService(), attributes, session);

      }
      catch (Exception ex2)
      {
        PontusMicrosoftGraphBaseProcessor.handleError(getLogger(), ex2, session);
      }
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
