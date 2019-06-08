package com.pontusvision.processors.office365;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.Message;
import com.microsoft.graph.requests.extensions.IMessageCollectionPage;
import com.microsoft.graph.requests.extensions.IMessageCollectionRequest;
import com.pontusvision.nifi.office365.PontusMicrosoftGraphAuthControllerServiceInterface;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

@Tags({ "GRAPH", "Message", "Microsoft" }) @CapabilityDescription("Get messages")

public class PontusMicrosoftGraphMessageProcessor extends AbstractProcessor {

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    private String                                             messageFields = null;
    private PontusMicrosoftGraphAuthControllerServiceInterface authProviderService;

    final static PropertyDescriptor MESSAGE_FIELDS = new PropertyDescriptor.Builder()
            .name("Message Fields").defaultValue("")
            .description("Message Fields to return from the Office 365 Graph API for Emails.  "
                + "If left blank, this will return all fields.  Examples: subject,body.content,sender,from,"
                + "toRecipients,ccRecipients").required(true)
            .build();

    final static PropertyDescriptor SERVICE = new PropertyDescriptor.Builder()
            .name("Auththentication Controller Service").displayName("Authentication Controller Service")
            .description("Controller Service to authenticate with Office 365 using oauth2").required(true)
            .identifiesControllerService(PontusMicrosoftGraphAuthControllerServiceInterface.class)
            .build();

    public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
            .description("Success relationship").build();

    public static final Relationship FAILURE = new Relationship.Builder().name("FAILURE")
            .description("Failure relationship").build();


    @Override public void init(final ProcessorInitializationContext context)
    {
        List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(MESSAGE_FIELDS);
        properties.add(SERVICE);

        this.properties = Collections.unmodifiableList(properties);

        Set<Relationship> relationships = new HashSet<>();
        relationships.add(FAILURE);
        relationships.add(SUCCESS);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    /*
     * Load Messages
     */
    private List<Message> loadMessages(String userId, IGraphServiceClient graphClient) throws Exception {
        IMessageCollectionRequest request = graphClient
                .users(userId)
                .messages()
                .buildRequest()
                .select(messageFields);

        List<Message> result = new ArrayList<Message>();

        do {
            IMessageCollectionPage page = request.get();
            List<Message> messages = page.getCurrentPage();

            if (messages != null && !messages.isEmpty()) {
                for (Message message : messages) {
                    result.add(message);
                }
            }

            // Get next page request
            if (page.getNextPage() != null) {
                request = page.getNextPage().buildRequest();
            } else {
                request = null;
            }
        }
        while (request != null);

        return result;
    }


    @Override public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue,
                                             final String newValue)
    {
        if (descriptor.equals(MESSAGE_FIELDS)) {
            messageFields = newValue;
        }
    }

    @Override public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
    {
        final ComponentLog log = this.getLogger();
        final FlowFile flowFile = session.get();

        if (flowFile == null) {
            return;
        }

        session.read(flowFile, new InputStreamCallback() {
            @Override
            public void process(InputStream in) throws IOException {
                StringBuffer strbuf = new StringBuffer();

                byte val = -1;
                do {
                    val = (byte) in.read();
                    if (val >= 0)
                    {
                        strbuf.append((char)val);
                    }

                } while (val != '\n' && val != -1);

                String userId = strbuf.toString();
                FlowFile ff = session.create();

                try {
                    ff = session.write(ff, new OutputStreamCallback() {

                        @Override
                        public void process(OutputStream out) throws IOException {
                            try {
                                List<Message> messages = loadMessages(userId, authProviderService.getService());

                                for (Message message : messages) {
                                    IOUtils.write(message.body.content, out, Charset.defaultCharset());
                                }
                            } catch (Exception ex) {
                                getLogger().error("Unable to read API", ex);
                            }

                        }
                    });

                    session.transfer(ff, SUCCESS);
                } catch (ProcessException ex) {
                    getLogger().error("Unable to process", ex);
                    session.transfer(ff, FAILURE);
                }
            }
        });
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
