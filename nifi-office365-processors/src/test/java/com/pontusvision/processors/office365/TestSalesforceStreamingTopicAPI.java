package com.pontusvision.processors.office365;

import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by jdyer on 4/2/16.
 */
public class TestSalesforceStreamingTopicAPI {

    private TestRunner testRunner;

//    @Before
//    public void init() {
//        testRunner = TestRunners.newTestRunner(SalesforceStreamingTopicAPI.class);
//    }

    @Test
    public void testProcessor() throws IOException {

        //testRunner.setProperty(GetHTMLElement.CSS_SELECTOR, "b");   //Bold element is not present in sample HTML

        //testRunner.enqueue("Something");
        //testRunner.enqueue(new File("src/test/resources/salesforce/SObject_Account_Fields.json").toPath());
        //testRunner.run();

//        testRunner.assertTransferCount(GetHTMLElement.REL_SUCCESS, 0);
//        testRunner.assertTransferCount(GetHTMLElement.REL_INVALID_HTML, 0);
//        testRunner.assertTransferCount(GetHTMLElement.REL_NOT_FOUND, 1);
    }
}
