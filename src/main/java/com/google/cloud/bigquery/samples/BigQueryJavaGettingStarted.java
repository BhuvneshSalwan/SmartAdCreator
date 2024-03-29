/*
 * Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.bigquery.samples;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.Bigquery.Datasets;
import com.google.api.services.bigquery.Bigquery.Jobs.Insert;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.DatasetList;
import com.google.api.services.bigquery.model.GetQueryResultsResponse;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationQuery;
import com.google.api.services.bigquery.model.JobReference;
import com.google.api.services.bigquery.model.TableCell;
import com.google.api.services.bigquery.model.TableRow;
import com.google.cloud.bigquery.views.Client;
import com.google.cloud.bigquery.views.Creative;
import com.hibernate.mysql.common.CommonFunctions;
import com.hibernate.mysql.common.FactoryClass;
import com.parse.campaign.request.ParseCampaignUpdater;

/**
 * Example of authorizing with BigQuery and reading from a public dataset.
 */
public class BigQueryJavaGettingStarted {

  // [START credentials]
  /////////////////////////
  // CHANGE ME!
  // USER GENERATED VALUES: you must fill in values specific to your application.
  //
  // Visit https://cloud.google.com/console to create a Project and generate an
  // OAuth 2.0 Client ID and Secret.
  // See the README for more info.
  // Then, add the Project ID below, and point the CLIENTSECRETS_LOCATION file
  // to the file you downloaded.
  /////////////////////////
  private static final String PROJECT_ID = "stellar-display-145814";
  private static final String CLIENTSECRETS_LOCATION = "C:\\Users\\bhuvnesh\\Eclipse_Workspace\\Xplanck\\Core\\bigquery-samples-java\\client_secret_154911048506-ign21bljsc6jmrdvrd84tqt3a6omk5be.apps.googleusercontent.com.json";

  static GoogleClientSecrets clientSecrets = loadClientSecrets();

    // Static variables for API scope, callback URI, and HTTP/JSON functions
  private static final List<String> SCOPES = Arrays.asList(BigqueryScopes.BIGQUERY);
  private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

  /** Global instances of HTTP transport and JSON factory objects. */
  private static final HttpTransport TRANSPORT = new NetHttpTransport();
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  private static GoogleAuthorizationCodeFlow flow = null;

    /** Directory to store user credentials. */
  private static final java.io.File DATA_STORE_DIR =
      new java.io.File(System.getProperty("user.home"), ".store/bq_sample");

  /**
   * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
   * globally shared instance across your application.
   */
  private static FileDataStoreFactory dataStoreFactory;

  /**
   * @param args
   * @throws IOException
   * @throws InterruptedException
 * @throws JSONException 
   */
  public static void main(String[] args) throws IOException, InterruptedException, JSONException {
    // Create a new BigQuery client authorized via OAuth 2.0 protocol
    // dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
    Bigquery bigquery = createAuthorizedClient();
    // Print out available datasets in the "publicdata" project to the console
    listDatasets(bigquery, PROJECT_ID);
    
    List<Creative> list = CommonFunctions.getSmartCreative();
    
    for(Creative creative : list)
    {
    	Client client = CommonFunctions.getClientById(CommonFunctions.getClientIdByCreativeId(creative.getId()));
    	
    	String hostname = client.getWebsite();
    	
    	String regexMatch = null;
    	
    	if(null == hostname){
    		continue;
    	}
    	else{
    		
    		if(hostname.substring(0, 8).equals("https://"))
    		{
    			regexMatch = hostname.substring(8);
    		}
    		
    		if(hostname.substring(0,7).equals("http://"))
    		{
    			regexMatch = hostname.substring(7);
    		}
    		
    		if(hostname.substring(0, 4).equals("www."))
    		{
    			regexMatch = hostname.substring(4);
    		}
    		
    		if(hostname.substring(0, 2).equals("//"))
    		{
    			regexMatch = hostname.substring(2);
    		}
    			
    	}
    	
    	if((null == creative.getRule() || creative.getRule().equals("")) && (regexMatch.equals("") || null == regexMatch))
    	{
    		continue;
    	}
    	
    	pushInput(bigquery, hostname, creative);
    }
    
    System.out.println("Updated Successfully");

    FactoryClass.factory.close();
    
    System.exit(0);
    
  }
  
  public static void pushInput(Bigquery bigquery, String hostname, Creative creative) throws IOException, InterruptedException, JSONException
  {
	// Start a Query Job
	    String querySql = "SELECT * FROM ["+PROJECT_ID+":table_output.product_smart] where hostname like '%"+hostname+"%' and ruleSmart = '"+creative.getRule()+"';";
	    JobReference jobId = startQuery(bigquery, PROJECT_ID, querySql);

	    // Poll for Query Results, return result output
	    Job completedJob = checkQueryResults(bigquery, PROJECT_ID, jobId);

	    // Return and display the results of the Query Job
	    displayQueryResults(bigquery, PROJECT_ID, completedJob, creative);
  }

  /** Authorizes the installed application to access user's protected data. */
  private static Credential authorize() throws IOException {
    dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
    // set up authorization code flow
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES).setDataStoreFactory(
        dataStoreFactory).build();
    // authorize
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
  }
  // [END credentials]

  /**
   * Creates an authorized BigQuery client service using the OAuth 2.0 protocol
   *
   * This method first creates a BigQuery authorization URL, then prompts the
   * user to visit this URL in a web browser to authorize access. The
   * application will wait for the user to paste the resulting authorization
   * code at the command line prompt.
   *
   * @return an authorized BigQuery client
   * @throws IOException
   */
  public static Bigquery createAuthorizedClient() throws IOException {

    Credential credential = authorize();
    return new Bigquery(TRANSPORT, JSON_FACTORY, credential);
  }

  /**
   * Display all BigQuery datasets associated with a project
   *
   * @param bigquery  an authorized BigQuery client
   * @param projectId a string containing the current project ID
   * @throws IOException
   */
  public static void listDatasets(Bigquery bigquery, String projectId)
      throws IOException {
    Datasets.List datasetRequest = bigquery.datasets().list(projectId);
    DatasetList datasetList = datasetRequest.execute();
    if (datasetList.getDatasets() != null) {
      List<DatasetList.Datasets> datasets = datasetList.getDatasets();
      System.out.println("Available datasets\n----------------");
      System.out.println(datasets.toString());
      for (DatasetList.Datasets dataset : datasets) {
        System.out.format("%s\n", dataset.getDatasetReference().getDatasetId());
      }
    }
  }

  // [START start_query]
  /**
   * Creates a Query Job for a particular query on a dataset
   *
   * @param bigquery  an authorized BigQuery client
   * @param projectId a String containing the project ID
   * @param querySql  the actual query string
   * @return a reference to the inserted query job
   * @throws IOException
   */
  public static JobReference startQuery(Bigquery bigquery, String projectId,
                                        String querySql) throws IOException {
    System.out.format("\nInserting Query Job: %s\n", querySql);

    Job job = new Job();
    JobConfiguration config = new JobConfiguration();
    JobConfigurationQuery queryConfig = new JobConfigurationQuery();
    config.setQuery(queryConfig);

    job.setConfiguration(config);
    queryConfig.setQuery(querySql);

    Insert insert = bigquery.jobs().insert(projectId, job);
    insert.setProjectId(projectId);
    JobReference jobId = insert.execute().getJobReference();

    System.out.format("\nJob ID of Query Job is: %s\n", jobId.getJobId());

    return jobId;
  }

  /**
   * Polls the status of a BigQuery job, returns Job reference if "Done"
   *
   * @param bigquery  an authorized BigQuery client
   * @param projectId a string containing the current project ID
   * @param jobId     a reference to an inserted query Job
   * @return a reference to the completed Job
   * @throws IOException
   * @throws InterruptedException
   */
  private static Job checkQueryResults(Bigquery bigquery, String projectId, JobReference jobId)
      throws IOException, InterruptedException {
    // Variables to keep track of total query time
    long startTime = System.currentTimeMillis();
    long elapsedTime;

    while (true) {
      Job pollJob = bigquery.jobs().get(projectId, jobId.getJobId()).execute();
      elapsedTime = System.currentTimeMillis() - startTime;
      System.out.format("Job status (%dms) %s: %s\n", elapsedTime,
          jobId.getJobId(), pollJob.getStatus().getState());
      if (pollJob.getStatus().getState().equals("DONE")) {
        return pollJob;
      }
      // Pause execution for one second before polling job status again, to
      // reduce unnecessary calls to the BigQUery API and lower overall
      // application bandwidth.
      Thread.sleep(1000);
    }
  }
  // [END start_query]

  // [START display_result]
  /**
   * Makes an API call to the BigQuery API
   *
   * @param bigquery     an authorized BigQuery client
   * @param projectId    a string containing the current project ID
   * @param completedJob to the completed Job
   * @throws IOException
 * @throws JSONException 
   */
  private static void displayQueryResults(Bigquery bigquery,
                                          String projectId, Job completedJob, Creative creative) throws IOException, JSONException {
    GetQueryResultsResponse queryResult = bigquery.jobs()
        .getQueryResults(
            projectId, completedJob
            .getJobReference()
            .getJobId()
        ).execute();
    List<TableRow> rows = queryResult.getRows();
    System.out.print("\nQuery Results:\n------------\n");
    
    JSONArray product_array = new JSONArray();
    
    for (TableRow row : rows) {
    	for (TableCell field : row.getF()) {
    	      System.out.printf("%-50s", field.getV());
    	}
    	JSONObject product = new JSONObject();
    	List<TableCell> list = row.getF();
    	product.put("name",list.get(4).getV());
    	product.put("description", "");
    	product.put("product_image_url", list.get(6).getV());
    	product.put("landing_page_url", list.get(3).getV());
    	System.out.println(product.toString());
    	product_array.put(product);
    }
    
    JSONObject request_data = new JSONObject();
    
    request_data.put("is_updated", 2);
    request_data.put("is_updated_creative", 1);
    request_data.put("products", product_array);
    try {
		ParseCampaignUpdater.dataInsertParse(creative.getParse_campaign_id(), request_data);
	} catch (Exception e) {
		e.printStackTrace();
	}
  }
  // [END display_result]

  /**
   * Helper to load client ID/Secret from file.
   *
   * @return a GoogleClientSecrets object based on a clientsecrets.json
   */
  private static GoogleClientSecrets loadClientSecrets() {
    try {
      InputStream inputStream = new FileInputStream(CLIENTSECRETS_LOCATION);
        Reader reader =
          new InputStreamReader(inputStream);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(new JacksonFactory(),
                reader);
        return clientSecrets;
    } catch (Exception e) {
      System.out.println("Could not load client secrets file " + CLIENTSECRETS_LOCATION);
      e.printStackTrace();
    }
    return null;
  }


}
