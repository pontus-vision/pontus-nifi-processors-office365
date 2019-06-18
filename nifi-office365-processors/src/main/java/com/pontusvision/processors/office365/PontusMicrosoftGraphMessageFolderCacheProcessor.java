package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.MailFolder;
import com.microsoft.graph.requests.extensions.IMailFolderDeltaCollectionPage;
import com.microsoft.graph.requests.extensions.IMailFolderDeltaCollectionRequest;
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
import static com.pontusvision.processors.office365.PontusMicrosoftGraphUserProcessor.OFFICE365_USER_ID;

@Tags({ "GRAPH", "Folder", "Microsoft", "Office 365" }) @CapabilityDescription("Get Message Folders")

public class PontusMicrosoftGraphMessageFolderCacheProcessor extends PontusMicrosoftGraphBaseProcessor
{
    private String messageFolderFields = null;

    final static PropertyDescriptor MESSAGE_FOLDER_FIELDS = new PropertyDescriptor.Builder()
            .name("Message Folder Fields").defaultValue(
                    "id,displayName,childFolderCount,parentFolderId,totalItemCount,unreadItemCount")
            .description("Message Folder Fields to return from the Office 365 Graph API for Mail Folders.  "
                    + "If left blank, this will return all fields.  Examples: id,displayName,childFolderCount,parentFolderId,"
                    + "totalItemCount,unreadItemCount").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    @Override public void init(final ProcessorInitializationContext context)
    {
        super.init(context);

        List<PropertyDescriptor> properties = new ArrayList<>(this.properties);
        properties.add(MESSAGE_FOLDER_FIELDS);

        this.properties = Collections.unmodifiableList(properties);
    }

    /*
     * Load Messages
     */
    private void loadFolders(String userId, IGraphServiceClient graphClient,
                              FlowFile flowFile, ProcessSession session, String delta) throws Exception
    {
        IMailFolderDeltaCollectionRequest request;
        if (delta != null) {
            request = graphClient
                    .users(userId)
                    .mailFolders()
                    .delta(delta)
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
                    FlowFile ff = session.create(flowFile);
                    ff = session.putAttribute(ff, OFFICE365_USER_ID, userId);
                    ff = session.putAttribute(ff, OFFICE365_FOLDER_ID, folder.id);
                    ff = session.putAttribute(ff, OFFICE365_CACHE_KEY,
                            String.format(OFFICE365_DELTA_KEY_FORMAT_MESSAGE, userId, folder.id));
                    writeFlowFile(ff, session, folder.getRawObject().toString(), SUCCESS);
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
                    ff = session.putAttribute(ff, OFFICE365_DELTA_KEY, String.format(OFFICE365_DELTA_KEY_FORMAT_FOLDER, userId));
                    writeFlowFile(ff, session, deltaLink, SUCCESS);
                }
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
    }

    @Override
    public void process(ProcessContext context, ProcessSession session, FlowFile flowFile, String key, String delta) throws Exception {

        String userId = key.split(Pattern.quote("|"))[1];

        try
        {
            loadFolders(userId, authProviderService.getService(), flowFile, session, delta);
        }
        catch (Exception ex)
        {
            authProviderService.refreshToken();
            loadFolders(userId, authProviderService.getService(), flowFile, session, delta);
        }
    }
}
