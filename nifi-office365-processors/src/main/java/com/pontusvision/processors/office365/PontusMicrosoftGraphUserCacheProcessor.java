package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.User;
import com.microsoft.graph.requests.extensions.IUserDeltaCollectionPage;
import com.microsoft.graph.requests.extensions.IUserDeltaCollectionRequest;
import com.pontusvision.processors.office365.base.PontusMicrosoftGraphBaseProcessor;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.*;

@Tags({ "GRAPH", "User", "Microsoft", "Office 365" }) @CapabilityDescription("Gets Office users, and adds the userID for each user"
        + " in the office365_user_id flow file attribute")
@DynamicProperty(name = "Generated FlowFile attribute name", value = "Generated FlowFile attribute value",
        expressionLanguageScope = ExpressionLanguageScope.VARIABLE_REGISTRY,
        description = "Specifies an attribute on generated FlowFiles defined by the Dynamic Property's key and value." +
                " If Expression Language is used, evaluation will be performed only once per batch of generated FlowFiles.")
public class PontusMicrosoftGraphUserCacheProcessor extends PontusMicrosoftGraphBaseProcessor
{
    private String userFields = null;

    final static PropertyDescriptor USER_FIELDS = new PropertyDescriptor.Builder()
            .name("User Fields").defaultValue("businessPhones,displayName,givenName,jobTitle,mail,mobilePhone,"
                    + "officeLocation,preferredLanguage,surname,userPrincipalName,id")
            .description("User Fields to return from the Office 365 Graph API for Users.  "
                    + "Examples: businessPhones,displayName,givenName,"
                    + "jobTitle,mail,mobilePhone,officeLocation,preferredLanguage,surname,userPrincipalName,id"
                    + "toRecipients,ccRecipients").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(true).build();


    @Override protected PropertyDescriptor getRegexPropertyDescriptor()
    {
        return CACHE_FILTER_REGEX_USER;
    }

    @Override public void init(final ProcessorInitializationContext context)
    {
        super.init(context);

        List<PropertyDescriptor> properties = new ArrayList<>(this.properties);
        properties.add(USER_FIELDS);

        this.properties = Collections.unmodifiableList(properties);
    }

    /*
     * Load users
     */
    private void loadUsers(IGraphServiceClient graphClient, FlowFile flowFile, ProcessSession session,
                           String delta) throws Exception
    {
        IUserDeltaCollectionRequest request;

        if (delta != null) {
            request = graphClient.users()
                    .delta(delta)
                    .buildRequest()
                    .select(userFields);
        } else {
            request = graphClient.users()
                    .delta()
                    .buildRequest()
                    .select(userFields);
        }

        do
        {
            IUserDeltaCollectionPage page  = request.get();
            List<User>               users = page.getCurrentPage();

            if (users != null && !users.isEmpty())
            {
                for (User user : users)
                {
                    FlowFile ff = session.create(flowFile);
                    ff = session.putAttribute(ff, OFFICE365_USER_ID, user.id);
                    ff = session.putAttribute(ff, OFFICE365_CACHE_KEY, String.format(OFFICE365_DELTA_KEY_FORMAT_FOLDER, user.id));
                    writeFlowFile(ff, session, user.getRawObject().toString(), SUCCESS);
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
                    ff = session.putAttribute(ff, OFFICE365_DELTA_KEY, OFFICE365_DELTA_KEY_FORMAT_USER);
                    writeFlowFile(ff, session, deltaLink, SUCCESS);
                }
            }
        }while (request != null);

    }

    @Override public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue,
                                             final String newValue)
    {
        if (descriptor.equals(USER_FIELDS))
        {
            userFields = newValue;
        }
    }

    @Override public void process(ProcessContext context, ProcessSession session, FlowFile flowFile, String key, String delta) throws Exception {
        try
        {
            loadUsers(authProviderService.getService(), flowFile, session, delta);
        }
        catch (Exception ex) {
            authProviderService.refreshToken();
            loadUsers(authProviderService.getService(), flowFile, session, delta);
        }
    }

    @Override public void process(ProcessContext context, ProcessSession session, FlowFile flowFile) throws Exception {
        try
        {
            loadUsers(authProviderService.getService(), flowFile, session, null);
        }
        catch (Exception ex) {
            authProviderService.refreshToken();
            loadUsers(authProviderService.getService(), flowFile, session, null);
        }
    }
}
