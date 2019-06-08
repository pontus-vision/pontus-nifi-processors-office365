package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.User;
import com.microsoft.graph.requests.extensions.IUserDeltaCollectionPage;
import com.microsoft.graph.requests.extensions.IUserDeltaCollectionRequest;
import com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.nio.charset.Charset;
import java.util.*;

@Tags({ "GRAPH", "User", "Microsoft", "Office 365" }) @CapabilityDescription("Gets Office users, and adds the userID for each user"
    + " in the office365_user_id flow file attribute")
public class PontusMicrosoftGraphUserProcessor extends AbstractProcessor
{

  private List<PropertyDescriptor> properties;
  private Set<Relationship>        relationships;

  public static final String OFFICE365_USER_ID = "office365_user_id";
  private String                                             userFields = null;
  private PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;

  final static PropertyDescriptor USER_FIELDS = new PropertyDescriptor.Builder()
      .name("User Fields").defaultValue("businessPhones,displayName,givenName,jobTitle,mail,mobilePhone,"
          + "officeLocation,preferredLanguage,surname,userPrincipalName,id")
      .description("User Fields to return from the Office 365 Graph API for Users.  "
          + "Examples: businessPhones,displayName,givenName,"
          + "jobTitle,mail,mobilePhone,officeLocation,preferredLanguage,surname,userPrincipalName,id"
          + "toRecipients,ccRecipients").addValidator(StandardValidators.NON_BLANK_VALIDATOR).required(true).build();

  final static PropertyDescriptor SERVICE = new PropertyDescriptor.Builder()
      .name("Controller Service").displayName("Controller Service")
      .description("Controller Service").required(true)
      .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
      .build();

  public static final Relationship ORIGINAL = new Relationship.Builder().name("Original")
                                                                       .description("Success relationship").build();

  public static final Relationship SUCCESS = new Relationship.Builder().name("Success")
                                                                       .description("Success relationship").build();

  public static final Relationship FAILURE = new Relationship.Builder().name("Failure")
                                                                       .description("Failure relationship").build();

  @Override public void init(final ProcessorInitializationContext context)
  {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(USER_FIELDS);
    properties.add(SERVICE);

    this.properties = Collections.unmodifiableList(properties);

    Set<Relationship> relationships = new HashSet<>();
    relationships.add(ORIGINAL);
    relationships.add(SUCCESS);
    relationships.add(FAILURE);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  public static void writeFlowFile (FlowFile flowFile, ProcessSession session, User user)
  {
    FlowFile ff = session.create(flowFile);
    final String data = user.getRawObject().getAsString();
    ff = session.write(ff, out -> IOUtils.write(data, out, Charset.defaultCharset()));
    ff = session.putAttribute(ff,OFFICE365_USER_ID, user.id);
    session.transfer(ff, SUCCESS);
  }


  /*
   * Load users
   */
  private void loadUsers(IGraphServiceClient graphClient,
                            FlowFile flowFile, ProcessSession session) throws Exception
  {
    IUserDeltaCollectionRequest request = graphClient.users()
                                                     .delta()
                                                     .buildRequest()
                                                     .select(userFields);


    do
    {
      IUserDeltaCollectionPage page  = request.get();
      List<User>               users = page.getCurrentPage();

      if (users != null && !users.isEmpty())
      {
        for (User user : users)
        {
          writeFlowFile(flowFile, session, user);
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
    if (descriptor.equals(USER_FIELDS))
    {
      userFields = newValue;
    }
    authProviderService = null;
  }

  @Override public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    final ComponentLog log      = this.getLogger();
    FlowFile           flowFile = session.get();

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

    try
    {
      loadUsers(authProviderService.getService(), flowFile,session);

      session.transfer (flowFile, ORIGINAL);
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
