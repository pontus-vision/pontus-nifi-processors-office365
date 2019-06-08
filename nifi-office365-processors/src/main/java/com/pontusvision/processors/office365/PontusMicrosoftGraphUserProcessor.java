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

@Tags({ "GRAPH", "User", "Microsoft" }) @CapabilityDescription("Gets Office users, and adds the userID for each user"
    + " in the office365_user_id flow file attribute")
public class PontusMicrosoftGraphUserProcessor extends AbstractProcessor
{

  private List<PropertyDescriptor> properties;
  private Set<Relationship>        relationships;

  private String                                             userFields = null;
  private PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;

  final static PropertyDescriptor USER_FIELDS = new PropertyDescriptor.Builder()
      .name("User Fields").defaultValue("").required(false)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

  final static PropertyDescriptor SERVICE = new PropertyDescriptor.Builder()
      .name("Controller Service").displayName("Controller Service")
      .description("Controller Service").required(true)
      .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
      .build();

  public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
                                                                       .description("Success relationship").build();

  public static final Relationship FAILURE = new Relationship.Builder().name("FAILURE")
                                                                       .description("Failure relationship").build();

  @Override public void init(final ProcessorInitializationContext context)
  {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(USER_FIELDS);
    properties.add(SERVICE);

    this.properties = Collections.unmodifiableList(properties);

    Set<Relationship> relationships = new HashSet<>();
    relationships.add(FAILURE);
    relationships.add(SUCCESS);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  /*
   * Load users
   */
  private List<String> loadUsers(IGraphServiceClient graphClient) throws Exception
  {
    IUserDeltaCollectionRequest request = graphClient.users()
                                                     .delta()
                                                     .buildRequest()
                                                     .select(userFields);

    List<String> result = new ArrayList<String>();

    do
    {
      IUserDeltaCollectionPage page  = request.get();
      List<User>               users = page.getCurrentPage();

      if (users != null && !users.isEmpty())
      {
        for (User user : users)
        {
          result.add(user.id);
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

    return result;
  }

  @Override public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue,
                                           final String newValue)
  {
    if (descriptor.equals(USER_FIELDS))
    {
      userFields = newValue;
    }
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

      List<String> users = loadUsers(authProviderService.getService());

      for (String userId : users)
      {

        flowFile = session.write(flowFile, out -> {
          IOUtils.write(userId, out, Charset.defaultCharset());

        });
        flowFile = session.putAttribute(flowFile,"office365_user_id", userId);
        session.transfer(flowFile, SUCCESS);

      }
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
