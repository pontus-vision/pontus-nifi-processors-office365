# pontus-nifi-processors-office365
## Summary
This project creates a docker image that has Apache Nifi Processors to call the Microsoft office 365 graph API.  This is an intermediary image, built "from scratch", with just the nar files, and nothing else under the /opt/nifi/nifi-current/lib/ directory structure.  The intent is for this image to be used by other more complete images that will then package the nar files with Nifi itself.

## Contents
 For convenience, both the controller and processors have been packaged in the same nar file.  

 The following items are available here:

### Controllers
There are 3 main types of controllers:

1. PontusMicrosoftGraphAuthControllerService - Secrets stored in NiFi, where the following properties are entered in the processor: 
  * AUTH_GRANT_TYPE
  * AUTH_CLIENT_ID
  * AUTH_CLIENT_SECRET
  * AUTH_TENANT_ID
  * AUTH_SCOPE

2. PontusMicrosoftGraphAuthControllerServiceEnvVars - Secrets are stored as environment variables; the same settings as above point to env vars that are then used to keep the secrets
3. PontusMicrosoftGraphAuthControllerServiceSecretFiles - Secrets are stored as files; useful to store the secrets as K8S/Docker Secrets

### Processors
There are several processors available:
1. PontusMicrosoftGraphGenericProcessor - this can be used to call just about any Microsoft Graph API Call
2. PontusMicrosoftGraph[Message|MessageFolder|User]Processor - this can be used to query email messages, folders or users; no pagination / caching is enabled.
3. PontusMicrosoftGraph[Message|MessageFolder|User]CacheProcessor - retrieves email messages, folders or users and adds the last entry's delta token to a distributed cache
4. PontusMicrosoftGraph[Message|MessageFolder|User]DeltaProcessor - retrieves email messages, folders or users and uses the last entry's delta token from a flow file property.

  




      
      





