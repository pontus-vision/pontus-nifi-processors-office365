package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.MailFolder;
import com.microsoft.graph.requests.extensions.*;
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

import static com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface.getStackTrace;
import static com.pontusvision.processors.office365.PontusMicrosoftGraphUserProcessor.OFFICE365_USER_ID;

@Tags({ "GRAPH", "Folder", "Microsoft", "Office 365" }) @CapabilityDescription("Get Message Folders")

public class PontusMicrosoftGraphMessageFolderDeltaProcessor extends AbstractProcessor
{

    private List<PropertyDescriptor> properties;
    private Set<Relationship>        relationships;

    public static final String OFFICE365_FOLDER_ID = "office365_folder_id";
    private String deltaField = null;
    private String messageFolderFields = null;
    private PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;

    final static PropertyDescriptor MESSAGE_FOLDER_FIELDS = new PropertyDescriptor.Builder()
            .name("Message Folder Fields").defaultValue(
                    "id,displayName,childFolderCount,parentFolderId,totalItemCount,unreadItemCount")
            .description("Message Folder Fields to return from the Office 365 Graph API for Mail Folders.  "
                    + "If left blank, this will return all fields.  Examples: id,displayName,childFolderCount,parentFolderId,"
                    + "totalItemCount,unreadItemCount").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    final static PropertyDescriptor SERVICE = new PropertyDescriptor.Builder()
            .name("Authentication Controller Service").displayName("Authentication Controller Service")
            .description("Controller Service to authenticate with Office 365 using oauth2").required(true)
            .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
            .build();

    final static PropertyDescriptor DELTA_FIELD_NAME = new PropertyDescriptor.Builder()
            .name("Delta Field Name").defaultValue("")
            .description("Delta field name").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(false).build();


    public static final Relationship SUCCESS = new Relationship.Builder().name("Success")
            .description("Success relationship").build();

    public static final Relationship FAILURE = new Relationship.Builder().name("Failure")
            .description("Failure relationship").build();

    public static final Relationship DELTA = new Relationship.Builder().name("Delta")
            .description("Delta relationship").build();


    //  public static final Relationship ORIGINAL = new Relationship.Builder().name("original")
    //                                                                       .description("Failure relationship").build();

    @Override public void init(final ProcessorInitializationContext context)
    {
        List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(SERVICE);
        properties.add(MESSAGE_FOLDER_FIELDS);
        properties.add(DELTA_FIELD_NAME);

        this.properties = Collections.unmodifiableList(properties);

        Set<Relationship> relationships = new HashSet<>();
        relationships.add(FAILURE);
        relationships.add(SUCCESS);
        relationships.add(DELTA);
        //    relationships.add(ORIGINAL);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    public static void writeFlowFile(FlowFile flowFile, ProcessSession session, MailFolder folder)
    {
        FlowFile ff = session.create(flowFile);
        final String data = folder.getRawObject().toString();
        ff = session.write(ff, out -> {
            IOUtils.write(data, out, Charset.defaultCharset());
        });
        ff = session.putAttribute(ff,OFFICE365_FOLDER_ID, folder.id);
        session.transfer(ff, SUCCESS);
    }

    /*
     * Load Messages
     */
    private void loadFolders(String userId, IGraphServiceClient graphClient,
                              Map<String, String> attribs, ProcessSession session, String deltaToken) throws Exception
    {
        IMailFolderDeltaCollectionRequest request;
        if (deltaToken != null) {
            request = graphClient
                    .users(userId)
                    .mailFolders()
                    .delta(deltaToken)
                    .buildRequest()
                    .select(messageFolderFields);
        } else {
            request = graphClient
                    .users(userId)
                    .mailFolders()
                    .delta()
                    .buildRequest()
                    .select(messageFolderFields);
        }
        do
        {
            IMailFolderDeltaCollectionPage page = request.get();
            List<MailFolder> folders = page.getCurrentPage();

            if (folders != null && !folders.isEmpty())
            {
                for (MailFolder folder : folders)
                {
                    FlowFile flowFile = session.create();
                    flowFile = session.putAllAttributes(flowFile, attribs);
                    writeFlowFile(flowFile, session, folder);
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
                String token = page.deltaLink();
                FlowFile ff = session.create();
                ff = session.putAttribute(ff,OFFICE365_USER_ID, userId);
                ff = session.write(ff, out -> IOUtils.write(token, out, Charset.defaultCharset()));
                session.transfer(ff, DELTA);
            }
        }
        while (request != null);

    }

    @Override public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue,
                                             final String newValue)
    {
        if (descriptor.equals(MESSAGE_FOLDER_FIELDS))
        {
            messageFolderFields = newValue;
        }
        if (descriptor.equals(DELTA_FIELD_NAME))
        {
            deltaField = newValue;
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

        String deltaToken = flowFile.getAttribute(deltaField);
        messageFolderFields = context.getProperty(MESSAGE_FOLDER_FIELDS).evaluateAttributeExpressions(flowFile).getValue();

        try
        {
            session.remove(flowFile);

            loadFolders(userId, authProviderService.getService(), attributes, session, deltaToken);
            //      session.transfer(flowFile, ORIGINAL);
        }
        catch (Exception ex)
        {
            try {
                authProviderService.refreshToken();
                loadFolders(userId, authProviderService.getService(), attributes, session, deltaToken);
            }
            catch (Exception e) {
                getLogger().error("Unable to process", ex);
                flowFile = session.create();
                flowFile = session.putAllAttributes(flowFile,attributes);

                flowFile = session.putAttribute(flowFile,"Office365.MessageFolderProcessor.Error", ex.getMessage());
                flowFile = session.putAttribute(flowFile,"Office365.MessageFolderProcessor.StackTrace", getStackTrace(ex));

                session.transfer(flowFile, FAILURE);
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
