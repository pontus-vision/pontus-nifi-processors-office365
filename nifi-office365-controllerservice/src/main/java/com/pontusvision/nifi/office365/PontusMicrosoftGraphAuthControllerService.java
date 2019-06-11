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
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Tags({ "Pontus", "Microsoft", "Graph", "Service",
    "Openid" }) @CapabilityDescription("Microsoft Graph Auth Service.") public class PontusMicrosoftGraphAuthControllerService
    extends AbstractControllerService implements PontusMicrosoftGraphAuthControllerServiceInterface
{

  public final static PropertyDescriptor AUTH_CLIENT_ID = new PropertyDescriptor.Builder().name("Auth client ID")
                                                                                          .description(
                                                                                              "specifies the Oauth2 client id")
                                                                                          .required(true).addValidator(
          StandardValidators.NON_BLANK_VALIDATOR)
                                                                                          .sensitive(false).build();

  public final static PropertyDescriptor AUTH_TENANT_ID = new PropertyDescriptor.Builder().name("Auth Tenant ID")
                                                                                          .description(
                                                                                              "specifies the Oauth2 client API Key")
                                                                                          .required(true)
                                                                                          .addValidator(
                                                                                              StandardValidators.NON_BLANK_VALIDATOR)
                                                                                          .sensitive(false).build();

  public final static PropertyDescriptor AUTH_CLIENT_SECRET = new PropertyDescriptor.Builder()
      .name("Auth client Secret")
      .description("specifies the Oauth2 client Secret").required(true)
      .sensitive(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR).build();

  public final static PropertyDescriptor AUTH_GRANT_TYPE = new PropertyDescriptor.Builder().name("Auth Grant Type")
                                                                                           .description(
                                                                                               "specifies the Oauth2 client Secret")
                                                                                           .required(true)
                                                                                           .defaultValue("client_credentials")
                                                                                           .addValidator(
                                                                                               StandardValidators.NON_BLANK_VALIDATOR)
                                                                                           .build();

  public final static PropertyDescriptor AUTH_SCOPE = new PropertyDescriptor.Builder().name("Auth Scope")
                                                                                      .description(
                                                                                          "specifies the Oauth2 client Secret")
                                                                                      .required(true)
                                                                                      .defaultValue("https://graph.microsoft.com/.default")
                                                                                      .addValidator(
                                                                                          StandardValidators.NON_BLANK_VALIDATOR)
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
    clientId = context.getProperty(AUTH_CLIENT_ID).getValue();
    tenantId = context.getProperty(AUTH_TENANT_ID).getValue();
    clientSecret = context.getProperty(AUTH_CLIENT_SECRET).getValue();
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

    /*
    @OnDisabled public void shutdown() throws IOException
    {
        authService.close();

    }
    */

  @Override public IGraphServiceClient getService()
  {
    return graphService;
  }

}
