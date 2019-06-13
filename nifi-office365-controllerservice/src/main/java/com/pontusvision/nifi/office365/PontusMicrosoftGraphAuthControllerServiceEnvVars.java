/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pontusvision.nifi.office365;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Tags({ "Pontus", "Microsoft", "Graph", "Service", "Environment Variables", "Env Vars", "Office 365",
    "Openid" }) @CapabilityDescription("Microsoft Graph Auth Service.")
public class PontusMicrosoftGraphAuthControllerServiceEnvVars
    extends AbstractControllerService implements PontusMicrosoftGraphAuthControllerServiceInterface
{

  public final static Validator ENV_VAR_VALIDATOR = (subject, input, context) -> {
    boolean isValid = System.getenv(input) != null;
    String explanation = isValid ?
        "Valid Environment Variable" :
        "Failed to read environment variable " + input + " for " + subject;
    ValidationResult.Builder builder = new ValidationResult.Builder();
    return builder.input(input).subject(subject).valid(isValid).explanation(explanation).build();
  };

  public final static PropertyDescriptor AUTH_CLIENT_ID =
      new PropertyDescriptor
          .Builder()
          .name("Auth client ID Env Var")
          .description("Specifies an environment variable that points to the Oauth2 client id.  "
              + "Hint: use Kubernetes secrets to set this env var safely")
          .required(true)
          .addValidator(ENV_VAR_VALIDATOR)
          .defaultValue("OFFICE_365_AUTH_CLIENT_ID")
          .sensitive(false)
          .build();

  public final static PropertyDescriptor AUTH_TENANT_ID =
      new PropertyDescriptor
          .Builder()
          .name("Auth Tenant ID")
          .description("Specifies an environment variable that points to the Oauth2 client API Key.  "
              + "Hint: use Kubernetes secrets to set this env var safely")
          .required(true)
          .addValidator(ENV_VAR_VALIDATOR)
          .defaultValue("OFFICE_365_AUTH_TENANT_ID")
          .sensitive(false)
          .build();

  public final static PropertyDescriptor AUTH_CLIENT_SECRET =
      new PropertyDescriptor
          .Builder()
          .name("Auth client Secret")
          .description("Specifies an environment variable that points to the Oauth2 client Secret.  "
              + "Hint: use Kubernetes secrets to set this env var safely")
          .required(true)
          .sensitive(false)
          .addValidator(ENV_VAR_VALIDATOR)
          .defaultValue("OFFICE_365_AUTH_CLIENT_SECRET")
          .build();

  public final static PropertyDescriptor AUTH_GRANT_TYPE =
      new PropertyDescriptor
          .Builder()
          .name("Auth Grant Type")
          .description("Specifies the authentication grant type (leave this alone unless you know what you are doing)")
          .required(true)
          .defaultValue("client_credentials")
          .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
          .build();

  public final static PropertyDescriptor AUTH_SCOPE =
      new PropertyDescriptor
          .Builder()
          .name("Auth Scope")
          .description(
              "specifies the scope URL for the authentication (leave this alone unless you know what you are doing)")
          .required(true)
          .defaultValue("https://graph.microsoft.com/.default")
          .addValidator(StandardValidators.URL_VALIDATOR)
          .build();

  private static final List<PropertyDescriptor> properties;

  static
  {
    final List<PropertyDescriptor> props = new ArrayList<>();
    props.add(AUTH_GRANT_TYPE);
    props.add(AUTH_CLIENT_ID);
    props.add(AUTH_CLIENT_SECRET);
    props.add(AUTH_TENANT_ID);
    props.add(AUTH_SCOPE);
    properties = Collections.unmodifiableList(props);
  }

  String clientId     = "your client id";
  String tenantId     = "your tenant id";
  String clientSecret = "your client secret";
  String grantType    = "client_credentials";
  String scope        = "https://graph.microsoft.com/.default";

  public IGraphServiceClient graphService;

  @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors()
  {
    return properties;
  }

  /**
   * @param context the configuration context
   * @throws InitializationException if unable to create a database connection
   */
  @OnEnabled public void onEnabled(final ConfigurationContext context) throws InitializationException
  {
    clientId = System.getenv(context.getProperty(AUTH_CLIENT_ID).getValue());
    tenantId = System.getenv(context.getProperty(AUTH_TENANT_ID).getValue());
    clientSecret = System.getenv(context.getProperty(AUTH_CLIENT_SECRET).getValue());
    grantType = context.getProperty(AUTH_GRANT_TYPE).getValue();
    scope = context.getProperty(AUTH_SCOPE).getValue();

    try
    {
      PontusMicrosoftGraphAuthProvider authProvider = PontusMicrosoftGraphAuthProvider.getInstance(tenantId,
          clientId, clientSecret, grantType, scope);

      graphService = GraphServiceClient.builder()
                                       .authenticationProvider(authProvider)
                                       .buildClient();
    }
    catch (Throwable t)
    {
      throw new InitializationException(t);
    }

  }

  @OnDisabled public void shutdown()
  {
    try
    {
      graphService.shutdown();
    }
    catch (Throwable t)
    {
      getLogger().error("Failed to shutdown office 365 auth controller");
    }
  }

  @Override public IGraphServiceClient getService()
  {
    return graphService;
  }

}
