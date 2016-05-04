/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.connector.integration.test.gotowebinar;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.connector.integration.test.base.ConnectorIntegrationTestBase;
import org.wso2.connector.integration.test.base.RestResponse;
import org.apache.commons.lang.StringUtils;

public class GotowebinarConnectorIntegrationTest extends ConnectorIntegrationTestBase {

    private Map<String, String> esbRequestHeadersMap = new HashMap<String, String>();

    private Map<String, String> apiRequestHeadersMap = new HashMap<String, String>();

    private String apiRequestUrl;

    /**
     * Set up the environment.
     */
    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        init("gotowebinar-connector-1.0.1-SNAPSHOT");

        esbRequestHeadersMap.put("Accept-Charset", "UTF-8");
        esbRequestHeadersMap.put("Content-Type", "application/json");

        apiRequestHeadersMap.putAll(esbRequestHeadersMap);
        apiRequestHeadersMap
                .put("Authorization", "OAuth oauth_token=" + connectorProperties.getProperty("accessToken"));

        apiRequestUrl = connectorProperties.getProperty("apiUrl") + "/G2W/rest";

        // Validate Pre-requisites, if not Tests are skipped.
        if (!validate()) {
            Assert.fail("Pre-requisites mentioned in the Readme file are not accomplished in order to run this Test Suite.");
        }
        connectorProperties.put("emailOpt", System.currentTimeMillis() + connectorProperties
                .getProperty("emailOpt"));
        connectorProperties.put("email", System.currentTimeMillis() + connectorProperties
                .getProperty("email"));
    }

    /**
     * Method to validate whether pre-requisites are accomplished.
     *
     * @return boolean validation status.
     */
    private boolean validate() throws IOException, JSONException {

        boolean isValidSession = false;
        boolean isAnyUpcoming = false;

        Calendar calendar = Calendar.getInstance();
        Calendar currentCalendar = Calendar.getInstance();
        DateFormat isoTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        String toTime = isoTimeFormat.format(calendar.getTime());
        calendar.add(Calendar.YEAR, -1);
        String fromTime = isoTimeFormat.format(calendar.getTime());

        connectorProperties.put("searchFromTime", fromTime);
        connectorProperties.put("searchToTime", toTime);

        currentCalendar.add(currentCalendar.MONTH, 1);
        connectorProperties.put("webinarStartTime", isoTimeFormat.format(currentCalendar.getTime()));
        currentCalendar.add(currentCalendar.HOUR, 1);
        connectorProperties.put("webinarEndTime", isoTimeFormat.format(currentCalendar.getTime()));
        connectorProperties.put("timeZone", currentCalendar.getTimeZone().getID());
        // Get all Historical webinars with in last year.
        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/historicalWebinars?fromTime=" + fromTime + "&toTime=" + toTime;
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray historicalWebinarArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        outerloop:
        for (int i = 0; i < historicalWebinarArray.length(); i++) {
            String webinarKey = historicalWebinarArray.getJSONObject(i).getString("webinarKey");

            // Get all session details which belongs to the listed webinar.
            apiEndPoint =
                    apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                            + webinarKey + "/sessions";
            apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
            JSONArray webinarSessionArray = new JSONArray(apiRestResponse.getBody().getString("output"));

            for (int j = 0; j < webinarSessionArray.length(); j++) {
                int noOfRegistrants = webinarSessionArray.getJSONObject(j).getInt("registrantsAttended");

                // If session has one or more registrants all the required properties are set and loop will be
                if (noOfRegistrants > 0) {
                    String sessionKey = webinarSessionArray.getJSONObject(j).getString("sessionKey");
                    connectorProperties.put("sessionKey", sessionKey);
                    connectorProperties.put("webinarKey", webinarKey);
                    connectorProperties.put("fromTime", historicalWebinarArray.getJSONObject(i).getJSONArray("times")
                            .getJSONObject(0).getString("startTime"));
                    connectorProperties.put("toTime", historicalWebinarArray.getJSONObject(i).getJSONArray("times")
                            .getJSONObject(0).getString("endTime"));
                    isValidSession = true;
                    break outerloop;
                }
            }
        }

        // List all upcoming webinars
        apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/upcomingWebinars";
        apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        // If there are one or more upcoming webinars #upcomingWebinarKey property will be set
        if (apiResponseArray.length() > 0) {
            isAnyUpcoming = true;
            connectorProperties.put("upcomingWebinarKey", apiResponseArray.getJSONObject(0).getString("webinarKey"));

            for (int w = 0; w < apiResponseArray.length(); w++) {
                String upcomingWebinarKeyToDeleteRegistrant = apiResponseArray.getJSONObject(w).getString("webinarKey");
                connectorProperties.put("upcomingWebinarKeyToDeleteRegistrant", upcomingWebinarKeyToDeleteRegistrant);
                String getSessionsApiEndPoint =
                        apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                                + upcomingWebinarKeyToDeleteRegistrant + "/sessions";
                RestResponse<JSONObject> getSessionsApiRestResponse = sendJsonRestRequest(getSessionsApiEndPoint, "GET", apiRequestHeadersMap);
                JSONArray webinarSessionArray = new JSONArray(getSessionsApiRestResponse.getBody().getString("output"));
                if (!getSessionsApiRestResponse.getBody().getString("output").equals("[]")) {
                    String getAttendeesApiEndPoint =
                            apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                                    + "/webinars/" + upcomingWebinarKeyToDeleteRegistrant + "/sessions/"
                                    + webinarSessionArray.getJSONObject(0).getString("sessionKey") + "/attendees";
                    RestResponse<JSONObject> getAttendeesApiRestResponse = sendJsonRestRequest(getAttendeesApiEndPoint, "GET",
                            apiRequestHeadersMap);
                    if (!getAttendeesApiRestResponse.getBody().getString("output").equals("[]")) {
                        JSONArray getAttendeesApiResponseArray = new JSONArray(getAttendeesApiRestResponse.getBody().getString("output"));
                        connectorProperties.put("webinarRegistrantKeyToDelete",
                                getAttendeesApiResponseArray.getJSONObject(0).getString("registrantKey"));
                        break;
                    }
                }
                if (StringUtils.isNotEmpty(connectorProperties.getProperty("webinarRegistrantKeyToDelete"))) {
                    break;
                } else if (StringUtils.isEmpty(connectorProperties.getProperty("webinarRegistrantKeyToDelete"))
                        && w == apiResponseArray.length() - 1) {
                    Assert.fail("Any of the upcoming webinar has attendee for past session.");
                }
            }
        }
        return (isValidSession && isAnyUpcoming);
    }

    /**
     * Test getWebinarById method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getWebinarById} integration test with mandatory parameters.")
    public void testGetWebinarByIdWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getWebinarById");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getWebinarById_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("numberOfRegistrants"), esbRestResponse.getBody()
                .getString("numberOfRegistrants"));
        Assert.assertEquals(apiRestResponse.getBody().getString("subject"),
                esbRestResponse.getBody().getString("subject"));
        Assert.assertEquals(apiRestResponse.getBody().getString("timeZone"),
                esbRestResponse.getBody().getString("timeZone"));
    }

    /**
     * Test listSessions method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listSessions} integration test with mandatory parameters.")
    public void testListSessionsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listSessions_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("sessionKey"), esbResponseArray
                .getJSONObject(0).getString("sessionKey"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("registrantsAttended"), esbResponseArray
                .getJSONObject(0).getString("registrantsAttended"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("startTime"), esbResponseArray.getJSONObject(0)
                .getString("startTime"));
    }

    /**
     * Test getSessionById method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionById} integration test with mandatory parameters.")
    public void testGetSessionByIdWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionById");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionById_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test listSessionAttendees method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listSessionAttendees} integration test with mandatory parameters.")
    public void testListSessionAttendeesWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listSessionAttendees");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listSessionAttendees_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test getOrganizerSessions method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getOrganizerSessions} integration test with mandatory parameters.")
    public void testGetOrganizerSessionsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getOrganizerSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getOrganizerSessions_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/sessions?fromTime="
                        + connectorProperties.getProperty("searchFromTime") + "&toTime="
                        + connectorProperties.getProperty("searchToTime");
        ;
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().toString(), apiRestResponse.getBody().toString());
    }

    /**
     * Test getOrganizerSessions method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getOrganizerSessions} integration test with negative case.")
    public void testGetOrganizerSessionsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getOrganizerSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getOrganizerSessions_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("invalidProperty")
                        + "/sessions?fromTime=" + connectorProperties.getProperty("searchFromTime")
                        + "&toTime=" + connectorProperties.getProperty("searchToTime");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getSessionPerformance method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionPerformance} integration test with mandatory parameters.")
    public void testGetSessionPerformanceWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionPerformance");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionPerformance_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/performance";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONObject esbResponse = esbRestResponse.getBody();
        JSONObject apiResponse = apiRestResponse.getBody();

        Assert.assertEquals(apiResponse.getJSONObject("attendance").getString("registrantCount"),
                esbResponse.getJSONObject("attendance").getString("registrantCount"));
        Assert.assertEquals(apiResponse.getJSONObject("attendance").getString("percentageAttendance"),
                esbResponse.getJSONObject("attendance").getString("percentageAttendance"));
        Assert.assertEquals(apiResponse.getJSONObject("pollsAndSurveys").getString("pollCount"),
                esbResponse.getJSONObject("pollsAndSurveys").getString("pollCount"));
        Assert.assertEquals(apiResponse.getJSONObject("pollsAndSurveys").getString("percentagePollsCompleted"),
                esbResponse.getJSONObject("pollsAndSurveys").getString("percentagePollsCompleted"));
    }

    /**
     * Test getSessionPerformance method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionPerformance} integration test with negative case.")
    public void testGetSessionPerformanceWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionPerformance");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionPerformance_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("invalidProperty") + "/performance";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getSessionPolls method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionPolls} integration test with mandatory parameters.")
    public void testGetSessionPollsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionPolls");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionPolls_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/polls";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getJSONArray("responses").length(), esbResponseArray
                .getJSONObject(0).getJSONArray("responses").length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("question"), esbResponseArray.getJSONObject(0)
                .getString("question"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("numberOfResponses"), esbResponseArray
                .getJSONObject(0).getString("numberOfResponses"));
    }

    /**
     * Test getSessionPolls method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionPolls} integration test with negative case.")
    public void testGetSessionPollsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionPolls");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionPolls_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("invalidProperty") + "/polls";
        RestResponse<JSONObject> apiRestResponse =
                sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getSessionQuestions method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionQuestions} integration test with mandatory parameters.")
    public void testGetSessionQuestionsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionQuestions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionQuestions_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/questions";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getJSONArray("answers").length(), esbResponseArray
                .getJSONObject(0).getJSONArray("answers").length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("question"), esbResponseArray
                .getJSONObject(0).getString("question"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("askedBy"), esbResponseArray
                .getJSONObject(0).getString("askedBy"));
    }

    /**
     * Test getSessionQuestions method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionQuestions} integration test with negative case.")
    public void testGetSessionQuestionsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionQuestions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionQuestions_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("invalidProperty") + "/questions";
        RestResponse<JSONObject> apiRestResponse =
                sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getSessionSurveys method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionSurveys} integration test with mandatory parameters.")
    public void testGetSessionSurveysWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionSurveys");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionSurveys_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/surveys";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test getSessionSurveys method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionSurveys} integration test with negative case.")
    public void testGetSessionSurveysWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionSurveys");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionSurveys_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("invalidProperty") + "/surveys";
        RestResponse<JSONObject> apiRestResponse =
                sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test createRegistrant method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createRegistrant} integration test with mandatory parameters.")
    public void testCreateRegistrantWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createRegistrant");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createRegistrant_mandatory.json");
        if (esbRestResponse.getHttpStatusCode() == 409) {
            Assert.fail("The user is already registered.");
        }
        String registrantKey = esbRestResponse.getBody().getString("registrantKey");
        connectorProperties.put("registrantKey", registrantKey);

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("upcomingWebinarKey") + "/registrants/"
                        + connectorProperties.getProperty("registrantKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("firstName"),
                connectorProperties.getProperty("firstName"));
        Assert.assertEquals(apiRestResponse.getBody().getString("lastName"),
                connectorProperties.getProperty("lastName"));
        Assert.assertEquals(apiRestResponse.getBody().getString("email"), connectorProperties.getProperty("email"));
    }

    /**
     * Test createRegistrant method with Optional Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createRegistrant} integration test with optional parameters.")
    public void testCreateRegistrantWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createRegistrant");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createRegistrant_optional.json");
        if (esbRestResponse.getHttpStatusCode() == 409) {
            Assert.fail("The user is already registered.");
        }
        String registrantKey = esbRestResponse.getBody().getString("registrantKey");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("upcomingWebinarKey") + "/registrants/" + registrantKey;
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("organization"),
                connectorProperties.getProperty("organization"));
        Assert.assertEquals(apiRestResponse.getBody().getString("industry"),
                connectorProperties.getProperty("industry"));
        Assert.assertEquals(apiRestResponse.getBody().getString("jobTitle"),
                connectorProperties.getProperty("jobTitle"));
    }

    /**
     * Test createRegistrant method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createRegistrant} integration test with negative case.")
    public void testCreateRegistrantWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createRegistrant");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createRegistrant_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("invalidUpcomingWebinarKey") + "/registrants";
        RestResponse<JSONObject> apiRestResponse =
                sendJsonRestRequest(apiEndPoint, "POST", apiRequestHeadersMap, "api_createRegistrant_negative.json");

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test listRegistrants method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listRegistrants} integration test with mandatory parameters.")
    public void testListRegistrantsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listRegistrants");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listRegistrants_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/registrants/";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("email"), esbResponseArray.getJSONObject(0)
                .getString("email"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("firstName"), esbResponseArray.getJSONObject(0)
                .getString("firstName"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("registrantKey"), esbResponseArray
                .getJSONObject(0).getString("registrantKey"));
    }

    /**
     * Test getRegistrantById method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateRegistrantWithMandatoryParameters"},
            description = "gotowebinar {getRegistrantById} integration test with mandatory parameters.")
    public void testGetRegistrantByIdWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getRegistrantById");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getRegistrantById_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("upcomingWebinarKey") + "/registrants/"
                        + connectorProperties.getProperty("registrantKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("email"), esbRestResponse.getBody().getString("email"));
        Assert.assertEquals(apiRestResponse.getBody().getString("firstName"),
                esbRestResponse.getBody().getString("firstName"));
        Assert.assertEquals(apiRestResponse.getBody().getString("registrationDate"), esbRestResponse.getBody()
                .getString("registrationDate"));
        Assert.assertEquals(apiRestResponse.getBody().getString("status"), esbRestResponse.getBody()
                .getString("status"));
    }

    /**
     * Test getRegistrationFields method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getRegistrantById} integration test with mandatory parameters.")
    public void testGetRegistrationFieldsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getRegistrationFields");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getRegistrationFields_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("webinarKey") + "/registrants/fields";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().toString(), esbRestResponse.getBody().toString());
    }

    /**
     * Test getRegistrationFields method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getRegistrationFields} integration test with negative case.")
    public void testGetRegistrationFieldsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getRegistrationFields");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getRegistrationFields_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("invalidProperty") + "/registrants/fields";
        RestResponse<JSONObject> apiRestResponse =
                sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test deleteRegistrant method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getRegistrantById} integration test with mandatory parameters.")
    public void testDeleteRegistrantWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:deleteRegistrant");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_deleteRegistrant_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + connectorProperties.getProperty("upcomingWebinarKeyToDeleteRegistrant") + "/registrants/"
                        + connectorProperties.getProperty("webinarRegistrantKeyToDelete");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 204);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test deleteRegistrant method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testGetRegistrantByIdWithMandatoryParameters", "testCreateRegistrantWithMandatoryParameters"},
            description = "gotowebinar {deleteRegistrant} integration test with negative case.")
    public void testDeleteRegistrantWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:deleteRegistrant");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_deleteRegistrant_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 400);
    }

    /**
     * Test listHistoricalWebinars method with Optional Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listHistoricalWebinars} integration test with optional parameters.")
    public void testListHistoricalWebinarsWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listHistoricalWebinars");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listHistoricalWebinars_optional.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/historicalWebinars?fromTime=" + connectorProperties.getProperty("fromTime") + "&toTime="
                        + connectorProperties.getProperty("toTime");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("subject"), esbResponseArray.getJSONObject(0)
                .getString("subject"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("webinarKey"), esbResponseArray
                .getJSONObject(0).getString("webinarKey"));
    }

    /**
     * Test listHistoricalWebinars method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listHistoricalWebinars} integration test with negative case.")
    public void testListHistoricalWebinarsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listHistoricalWebinars");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listHistoricalWebinars_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/historicalWebinars?fromTime=" + connectorProperties.getProperty("fromTime")
                        + "&toTime=" + connectorProperties.getProperty("invalidProperty");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test listUpcomingWebinars method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listUpcomingWebinars} integration test with mandatory parameters.")
    public void testListUpcomingWebinarsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listUpcomingWebinars");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listUpcomingWebinars_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/upcomingWebinars";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("subject"), esbResponseArray.getJSONObject(0)
                .getString("subject"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("registrationUrl"), esbResponseArray
                .getJSONObject(0).getString("registrationUrl"));
        Assert.assertEquals(
                apiResponseArray.getJSONObject(0).getJSONArray("times").getJSONObject(0).getString("startTime"),
                esbResponseArray.getJSONObject(0).getJSONArray("times").getJSONObject(0).getString("startTime"));
    }

    /**
     * Test cancelWebinar method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateWebinarWithMandatoryParameters"},
            description = "gotowebinar {cancelWebinar} integration test with mandatory parameters.")
    public void testCancelWebinarWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:cancelWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_cancelWebinar_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("newWebinarKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 204);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test cancelWebinar method with Optional Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateWebinarWithOptionalParameters"},
            description = "gotowebinar {cancelWebinar} integration test with optional parameters.")
    public void testCancelWebinarWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:cancelWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_cancelWebinar_optional.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("newWebinarKeyOpt");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 204);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test cancelWebinar method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {cancelWebinar} integration test with negative case.")
    public void testCancelWebinarWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:cancelWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_cancelWebinar_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test createPanelists method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createPanelists} integration test with mandatory parameters.")
    public void testCreatePanelistsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createPanelists");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createPanelists_mandatory.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 201);
    }

    /**
     * Test createPanelists method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createPanelists} integration test with negative case.")
    public void testCreatePanelistsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createPanelists");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createPanelists_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test createWebinar method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createWebinar} integration test with mandatory parameters.")
    public void testCreateWebinarWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createWebinar_mandatory.json");
        String webinarKey = esbRestResponse.getBody().getString("webinarKey");
        connectorProperties.put("newWebinarKey", webinarKey);
        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + webinarKey;
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 201);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(webinarKey, apiRestResponse.getBody().getString("webinarKey"));
    }

    /**
     * Test createWebinar method with Optional Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createWebinar} integration test with optional parameters.")
    public void testCreateWebinarWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createWebinar_optional.json");

        String webinarKey = esbRestResponse.getBody().getString("webinarKey");
        connectorProperties.put("newWebinarKeyOpt", webinarKey);
        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars/"
                        + esbRestResponse.getBody().getString("webinarKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 201);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(esbRestResponse.getBody().getString("webinarKey"),
                apiRestResponse.getBody().getString("webinarKey"));
    }

    /**
     * Test createWebinar method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createWebinar} integration test with negative case.")
    public void testCreateWebinarWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createWebinar_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 400);
    }

    /**
     * Test getAllWebinars method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getAllWebinars} integration test with mandatory parameters.")
    public void testGetAllWebinarsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAllWebinars");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAllWebinars_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey") + "/webinars";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("webinarKey"), esbResponseArray
                .getJSONObject(0).getString("webinarKey"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("description"), esbResponseArray
                .getJSONObject(0).getString("description"));
    }

    /**
     * Test getAllWebinars method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getAllWebinars} integration test with negative case.")
    public void testGetAllWebinarsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAllWebinars");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAllWebinars_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("invalidProperty") + "/webinars";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getAttendeesForAllWebinarSessions method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getAttendeesForAllWebinarSessions} integration test with mandatory parameters.")
    public void testGetAttendeesForAllWebinarSessionsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeesForAllWebinarSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeesForAllWebinarSessions_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/attendees";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("email"), esbResponseArray.getJSONObject(0)
                .getString("email"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("sessionKey"), esbResponseArray
                .getJSONObject(0).getString("sessionKey"));
    }

    /**
     * Test getAttendeesForAllWebinarSessions method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getAttendeesForAllWebinarSessions} integration test with negative case.")
    public void testGetAttendeesForAllWebinarSessionsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeesForAllWebinarSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeesForAllWebinarSessions_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("invalidProperty") + "/attendees";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getAudioInformation method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getAudioInformation} integration test with mandatory parameters.")
    public void testGetAudioInformationWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAudioInformation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAudioInformation_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/audio";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        connectorProperties.put("organizer", esbRestResponse.getBody().getJSONObject("confCallNumbers")
                .getJSONObject("US").getJSONObject("accessCodes").getString("organizer"));
        connectorProperties.put("panelist", esbRestResponse.getBody().getJSONObject("confCallNumbers")
                .getJSONObject("US").getJSONObject("accessCodes").getString("panelist"));
        connectorProperties.put("attendee", esbRestResponse.getBody().getJSONObject("confCallNumbers")
                .getJSONObject("US").getJSONObject("accessCodes").getString("attendee"));
        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getJSONObject("confCallNumbers").toString(),
                apiRestResponse.getBody().getJSONObject("confCallNumbers").toString());
    }

    /**
     * Test getAudioInformation method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getAudioInformation} integration test with negative case.")
    public void testGetAudioInformationWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAudioInformation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAudioInformation_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("invalidProperty") + "/audio";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getPerformanceForAllWebinarSessions method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getPerformanceForAllWebinarSessions} integration test with mandatory parameters.")
    public void testGetPerformanceForAllWebinarSessionsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getPerformanceForAllWebinarSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getPerformanceForAllWebinarSessions_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/performance";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().toString(), apiRestResponse.getBody().toString());
    }

    /**
     * Test getPerformanceForAllWebinarSessions method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getPerformanceForAllWebinarSessions} integration test with negative case.")
    public void testGetPerformanceForAllWebinarSessionsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getPerformanceForAllWebinarSessions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getPerformanceForAllWebinarSessions_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("invalidProperty") + "/performance";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getWebinarMeetingTimes method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getWebinarMeetingTimes} integration test with mandatory parameters.")
    public void testGetWebinarMeetingTimesWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getWebinarMeetingTimes");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getWebinarMeetingTimes_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/meetingtimes";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("startTime"), esbResponseArray.getJSONObject(0)
                .getString("startTime"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("endTime"), esbResponseArray
                .getJSONObject(0).getString("endTime"));
    }

    /**
     * Test getWebinarMeetingTimes method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getWebinarMeetingTimes} integration test with negative case.")
    public void testGetWebinarMeetingTimesWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getWebinarMeetingTimes");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getWebinarMeetingTimes_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("invalidProperty") + "/meetingtimes";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getWebinarPanelists method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getWebinarPanelists} integration test with mandatory parameters.")
    public void testGetWebinarPanelistsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getWebinarPanelists");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getWebinarPanelists_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/panelists";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);
        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));

        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("email"), esbResponseArray.getJSONObject(0)
                .getString("email"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("panelistId"), esbResponseArray
                .getJSONObject(0).getString("panelistId"));
    }

    /**
     * Test getWebinarPanelists method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getWebinarPanelists} integration test with negative case.")
    public void testGetWebinarPanelistsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getWebinarPanelists");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getWebinarPanelists_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("invalidProperty") + "/panelists";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test updateAudioInformation method with Mandatory Parameters.
     */
    @Test(enabled = false, groups = {"wso2.esb"}, dependsOnMethods = {"testGetAudioInformationWithMandatoryParameters"},
            description = "gotowebinar {updateAudioInformation} integration test with mandatory parameters.")
    public void testUpdateAudioInformationWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:updateAudioInformation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_updateAudioInformation_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("upcomingWebinarKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(connectorProperties.getProperty("tollFreeCountries").split(",")[0],
                apiRestResponse.getBody().getJSONObject("pstnInfo").getJSONArray("tollFreeCountries").getString(0));
        Assert.assertEquals(connectorProperties.getProperty("tollFreeCountries").split(",").length,
                apiRestResponse.getBody().getJSONObject("pstnInfo").getJSONArray("tollFreeCountries").length());
    }

    /**
     * Test updateAudioInformation method with Optional Parameters.
     */
    @Test(enabled = false, groups = {"wso2.esb"}, description = "gotowebinar {updateAudioInformation} integration test with optional parameters.")
    public void testUpdateAudioInformationWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:updateAudioInformation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_updateAudioInformation_optional.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("upcomingWebinarKey") + "/panelists";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(connectorProperties.getProperty("organizer"),
                apiRestResponse.getBody().getJSONObject("privateInfo").getString("organizer"));
    }

    /**
     * Test updateAudioInformation method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {updateAudioInformation} integration test with negative case.")
    public void testUpdateAudioInformationWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:updateAudioInformation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_updateAudioInformation_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test updateWebinar method with Mandatory Parameters.
     */
    @Test(enabled = false, groups = {"wso2.esb"}, description = "gotowebinar {updateWebinar} integration test with mandatory parameters.")
    public void testUpdateWebinarWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:updateWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_updateWebinar_mandatory.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), "202");
    }

    /**
     * Test updateWebinar method with Optional Parameters.
     */
    @Test(enabled = false, groups = {"wso2.esb"}, description = "gotowebinar {updateWebinar} integration test with optional parameters.")
    public void testUpdateWebinarWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:updateWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_updateWebinar_optional.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("upcomingWebinarKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 202);
        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(esbRestResponse.getBody().getString("description"), apiRestResponse.getBody()
                .getString("description"));
    }

    /**
     * Test updateWebinar method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {updateWebinar} integration test with negative case.")
    public void testUpdateWebinarWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:updateWebinar");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_updateWebinar_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 404);
    }

    /**
     * Test getAttendee method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testGetSessionAttendeesWithMandatoryParameters"},
            description = "gotowebinar {getAttendee} integration test with mandatory parameters.")
    public void testGetAttendeeWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendee");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendee_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("webinarRegistrantKey");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getBody().getString("registrationDate"),
                esbRestResponse.getBody().getString("registrationDate"));
        Assert.assertEquals(apiRestResponse.getBody().getString("registrantKey"),
                esbRestResponse.getBody().getString("registrantKey"));
    }

    /**
     * Test getAttendee method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateRegistrantWithMandatoryParameters"},
            description = "gotowebinar {getAttendee} integration test with negative case.")
    public void testGetAttendeeWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendee");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendee_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("invalidProperty");
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getAttendeePollAnswers method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testGetSessionAttendeesWithMandatoryParameters"},
            description = "gotowebinar {getAttendeePollAnswers} integration test with mandatory parameters.")
    public void testGetAttendeePollAnswersWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeePollAnswers");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeePollAnswers_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("webinarRegistrantKey") + "/polls";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 200);
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test getAttendeePollAnswers method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateRegistrantWithMandatoryParameters"},
            description = "gotowebinar {getAttendeePollAnswers} integration test with negative case.")
    public void testGetAttendeePollAnswersWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeePollAnswers");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeePollAnswers_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("invalidProperty") + "/polls";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getAttendeeQuestions method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testGetSessionAttendeesWithMandatoryParameters"},
            description = "gotowebinar {getAttendeeQuestions} integration test with mandatory parameters.")
    public void testGetAttendeeQuestionsWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeeQuestions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeeQuestions_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("webinarRegistrantKey") + "/questions";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test getAttendeeQuestions method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateRegistrantWithMandatoryParameters"},
            description = "gotowebinar {getAttendeeQuestions} integration test with negative case.")
    public void testGetAttendeeQuestionsWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeeQuestions");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeeQuestions_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("invalidProperty") + "/questions";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getAttendeeSurveyAnswers method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testGetSessionAttendeesWithMandatoryParameters"},
            description = "gotowebinar {getAttendeeSurveyAnswers} integration test with mandatory parameters.")
    public void testGetAttendeeSurveyAnswersWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeeSurveyAnswers");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeeSurveyAnswers_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("webinarRegistrantKey") + "/surveys";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test getAttendeeSurveyAnswers method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateRegistrantWithMandatoryParameters"},
            description = "gotowebinar {getAttendeeSurveyAnswers} integration test with negative case.")
    public void testGetAttendeeSurveyAnswersWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getAttendeeSurveyAnswers");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getAttendeeSurveyAnswers_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees/"
                        + connectorProperties.getProperty("invalidProperty") + "/surveys";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test getSessionAttendees method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionAttendees} integration test with mandatory parameters.")
    public void testGetSessionAttendeesWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionAttendees");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionAttendees_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("sessionKey") + "/attendees";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        JSONArray apiResponseArray = new JSONArray(apiRestResponse.getBody().getString("output"));
        connectorProperties.put("webinarRegistrantKey", esbResponseArray.getJSONObject(0).getString("registrantKey"));
        Assert.assertEquals(apiResponseArray.length(), esbResponseArray.length());
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("firstName"), esbResponseArray.getJSONObject(0)
                .getString("firstName"));
        Assert.assertEquals(apiResponseArray.getJSONObject(0).getString("lastName"), esbResponseArray
                .getJSONObject(0).getString("lastName"));
    }

    /**
     * Test getSessionAttendees method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {getSessionAttendees} integration test with negative case.")
    public void testGetSessionAttendeesWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:getSessionAttendees");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_getSessionAttendees_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("webinarKey") + "/sessions/"
                        + connectorProperties.getProperty("invalidProperty") + "/attendees";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test createCOOrganizer method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createCOOrganizer} integration test with mandatory parameters.")
    public void testCreateCOOrganizerWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createCOOrganizer");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createCOOrganizer_mandatory.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 201);
    }

    /**
     * Test createCOOrganizer method with Optional Parameters.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createCOOrganizer} integration test with optional parameters.")
    public void testCreateCOOrganizerWithOptionalParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createCOOrganizer");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createCOOrganizer_optional.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 201);
    }

    /**
     * Test createCOOrganizer method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {createCOOrganizer} integration test with negative case.")
    public void testCreateCOOrganizerWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:createCOOrganizer");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_createCOOrganizer_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 400);
    }

    /**
     * Test deleteCOOrganizer method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testResendInvitationWithMandatoryParameters"},
            description = "gotowebinar {deleteCOOrganizer} integration test with mandatory parameters.")
    public void testDeleteCOOrganizerWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:deleteCOOrganizer");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_deleteCOOrganizer_mandatory.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 204);
    }

    /**
     * Test deleteCOOrganizer method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {deleteCOOrganizer} integration test with negative case.")
    public void testDeleteCOOrganizerWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:deleteCOOrganizer");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_deleteCOOrganizer_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 400);
    }

    /**
     * Test listCOOrganizers method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testCreateCOOrganizerWithMandatoryParameters"}, description = "gotowebinar {listCOOrganizers} integration test with mandatory parameters.")
    public void testListCOOrganizersWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listCOOrganizers");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listCOOrganizers_mandatory.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("upcomingWebinarKey") + "/coorganizers";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        JSONArray esbResponseArray = new JSONArray(esbRestResponse.getBody().getString("output"));
        String coorganizerKey = esbResponseArray.getJSONObject(0).getString("memberKey");
        String isExternalCOOrganizer = esbResponseArray.getJSONObject(0).getString("external");
        connectorProperties.put("coorganizerKey", coorganizerKey);
        connectorProperties.put("isExternalCOOrganizer", isExternalCOOrganizer);

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), apiRestResponse.getHttpStatusCode());
        Assert.assertEquals(esbRestResponse.getBody().getString("output").toString(),
                apiRestResponse.getBody().getString("output").toString());
    }

    /**
     * Test listCOOrganizers method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {listCOOrganizers} integration test with negative case.")
    public void testListCOOrganizersWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:listCOOrganizers");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_listCOOrganizers_negative.json");

        String apiEndPoint =
                apiRequestUrl + "/organizers/" + connectorProperties.getProperty("organizerKey")
                        + "/webinars/" + connectorProperties.getProperty("invalidProperty") + "/coorganizers";
        RestResponse<JSONObject> apiRestResponse = sendJsonRestRequest(apiEndPoint, "GET", apiRequestHeadersMap);

        Assert.assertEquals(apiRestResponse.getHttpStatusCode(), esbRestResponse.getHttpStatusCode());
        Assert.assertEquals(apiRestResponse.getBody().getString("errorCode"),
                esbRestResponse.getBody().getString("errorCode"));
        Assert.assertEquals(apiRestResponse.getBody().getString("description"),
                esbRestResponse.getBody().getString("description"));
    }

    /**
     * Test resendInvitation method with Mandatory Parameters.
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testListCOOrganizersWithMandatoryParameters"}, description = "gotowebinar {resendInvitation} integration test with mandatory parameters.")
    public void testResendInvitationWithMandatoryParameters() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:resendInvitation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_resendInvitation_mandatory.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 204);
    }

    /**
     * Test resendInvitation method with Negative case.
     */
    @Test(groups = {"wso2.esb"}, description = "gotowebinar {resendInvitation} integration test with negative case.")
    public void testResendInvitationWithNegativeCase() throws IOException, JSONException {

        esbRequestHeadersMap.put("Action", "urn:resendInvitation");
        RestResponse<JSONObject> esbRestResponse =
                sendJsonRestRequest(proxyUrl, "POST", esbRequestHeadersMap, "esb_resendInvitation_negative.json");

        Assert.assertEquals(esbRestResponse.getHttpStatusCode(), 400);
    }
}
