/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distri buted with
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
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;

import java.io.PrintWriter;
import java.io.StringWriter;

@Tags({ "Pontus", "Microsoft", "Service", "Graph",
    "Auth" }) @CapabilityDescription("Microsoft Graph Auth Service.") public interface PontusMicrosoftGraphAuthControllerServiceInterface
    extends ControllerService
{

  IGraphServiceClient getService();

  public void refreshToken();

  public static String getStackTrace(Throwable e)
  {
    StringWriter sw = new StringWriter();
    PrintWriter  pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString();
  }


}
