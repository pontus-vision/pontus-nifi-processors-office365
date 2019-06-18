package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.Attachment;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.Message;
import com.microsoft.graph.requests.extensions.IAttachmentCollectionPage;
import com.microsoft.graph.requests.extensions.IAttachmentCollectionRequest;
import com.microsoft.graph.requests.extensions.IMessageDeltaCollectionPage;
import com.microsoft.graph.requests.extensions.IMessageDeltaCollectionRequest;
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
import java.util.regex.Pattern;

import static com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface.getStackTrace;
import static com.pontusvision.processors.office365.PontusMicrosoftGraphMessageFolderDeltaProcessor.OFFICE365_FOLDER_ID;
import static com.pontusvision.processors.office365.PontusMicrosoftGraphUserProcessor.OFFICE365_USER_ID;

@Tags({ "GRAPH", "Message", "Microsoft", "Office 365" }) @CapabilityDescription("Get messages")

public class PontusMicrosoftGraphMessageCacheProcessor extends PontusMicrosoftGraphBaseProcessor
{
    private String messageFields = null;

    final static PropertyDescriptor MESSAGE_FIELDS = new PropertyDescriptor.Builder()
            .name("Message Fields").defaultValue(
                    "id,createdDateTime,lastModifiedDateTime,changeKey,categories,receivedDateTime,sentDateTime,hasAttachments,internetMessageId,subject,bodyPreview,importance,parentFolderId,conversationId,isDeliveryReceiptRequested,isReadReceiptRequested,isRead,isDraft,webLink,inferenceClassification,body,sender,from,toRecipients,ccRecipients,bccRecipients,replyTo,flag")
            .description("Message Fields to return from the Office 365 Graph API for Emails.  "
                    + "If left blank, this will return all fields.  Examples: subject,body.content,sender,from,"
                    + "toRecipients,ccRecipients").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    public static final Relationship SUCCESS_MESSAGES = new Relationship.Builder().name("success_messages")
            .description(
                    "Success relationship for messages")
            .build();

    public static final Relationship SUCCESS_ATTACHMENTS = new Relationship.Builder().name("success_attachments")
            .description(
                    "Success relationship for attachments")
            .build();

    @Override public void init(final ProcessorInitializationContext context)
    {
        super.init(context);

        List<PropertyDescriptor> properties = new ArrayList<>(this.properties);
        properties.add(MESSAGE_FIELDS);

        this.properties = Collections.unmodifiableList(properties);

        Set<Relationship> relationships = new HashSet<>();
        relationships.add(FAILURE);
        relationships.add(SUCCESS_MESSAGES);
        relationships.add(SUCCESS_ATTACHMENTS);

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
                    FlowFile ff = session.create(flowFile);
                    writeFlowFile(ff, session, attachment.getRawObject().toString(), SUCCESS_ATTACHMENTS);
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

    /*
     * Load Messages
     */
    private void loadMessages(String userId, String folderId, IGraphServiceClient graphClient,
                              FlowFile flowFile, ProcessSession session, String delta) throws Exception
    {
        IMessageDeltaCollectionRequest request;
        if (delta != null && delta.trim().length() > 0) {
            request = graphClient
                    .users(userId)
                    .mailFolders(folderId)
                    .messages()
                    .delta(delta)
                    .buildRequest().top(10)
                    .select(messageFields);
        } else {
            request = graphClient
                    .users(userId)
                    .mailFolders(folderId)
                    .messages()
                    .delta()
                    .buildRequest().top(10)
                    .select(messageFields);

        }

        do
        {
            IMessageDeltaCollectionPage page = request.get();
            List<Message> messages = page.getCurrentPage();

            if (messages != null && !messages.isEmpty())
            {
                for (Message message : messages)
                {
                    FlowFile ff = session.create(flowFile);
                    loadAttachments(userId, message, graphClient, ff, session);
                    ff = session.putAttribute(ff, OFFICE365_USER_ID, userId);
                    ff = session.putAttribute(ff, OFFICE365_FOLDER_ID, folderId);
                    ff = session.putAttribute(ff, OFFICE365_MESSAGE_ID,  message.id);
                    writeFlowFile(ff, session, message.getRawObject().toString(), SUCCESS_MESSAGES);
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
                String deltaLink = page.deltaLink();
                if (!deltaLink.equals(delta)) {
                    FlowFile ff = session.create(flowFile);
                    ff = session.putAttribute(ff, OFFICE365_DELTA_VALUE, deltaLink);
                    ff = session.putAttribute(ff, OFFICE365_DELTA_KEY,
                            String.format(OFFICE365_DELTA_KEY_FORMAT_MESSAGE, userId, folderId));
                    writeFlowFile(ff, session, deltaLink, SUCCESS_MESSAGES);
                }
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
            flowFile = session.create();
        }

        session.remove(flowFile);

        try
        {
            Set<String> keys = cacheClient.keySet(deserializer);

            long counter = 0;

            for (String key : keys)
            {
                if (cacheFilterRegex.matcher(key).matches())
                {
                    counter++;
                    process(context, session, flowFile, key, cacheClient.get(key, serializer, deserializer));
                }
            }
            if (counter == 0)
            {
                process(context, session, flowFile);
            }

//            session.transfer(flowFile, ORIGINAL);
        }
        catch (Exception ex)
        {
            getLogger().error("Unable to process", ex);

            session.remove(flowFile);

            flowFile = session.create();
            flowFile = session.putAttribute(flowFile, "Office365.Error", ex.getMessage());
            flowFile = session.putAttribute(flowFile, "Office365.StackTrace", getStackTrace(ex));
            session.transfer(flowFile, FAILURE);
        }
    }

    @Override
    public void process(ProcessContext context, ProcessSession session, FlowFile flowFile, String key, String delta) throws Exception {

        String[] fields = key.split(Pattern.quote("|"));
        String userId = fields[1];
        String folderId = fields[2];

        try
        {
            loadMessages(userId, folderId, authProviderService.getService(), flowFile, session, delta);
        }
        catch (Exception ex)
        {
            authProviderService.refreshToken();
            loadMessages(userId, folderId, authProviderService.getService(), flowFile, session, delta);
        }
    }

}
