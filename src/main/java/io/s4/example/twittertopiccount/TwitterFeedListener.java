/*
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 	        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License. See accompanying LICENSE file. 
 */
package io.s4.example.twittertopiccount;

import io.s4.client.Driver;
import io.s4.client.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.util.EncodingUtil;


public class TwitterFeedListener {
    private String userid;
    private String password;
    private String urlString = "https://stream.twitter.com/1/statuses/sample.json";
    private String clientAdapterHost = "localhost";
    private int clientAdapterPort = 2334;
    private long maxBackoffTime = 30 * 1000; // 5 seconds
    private long messageCount = 0;
    private long blankCount = 0;
    private String streamName = "RawStatus";
    private Map<String, String> fieldMap = new HashMap<String, String>();
    private Driver driver;
    private LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<String>();
    
    public TwitterFeedListener() {
        fieldMap.put("in_reply_to_screen_name", "inReplyToScreenName");
        fieldMap.put("screen_name", "screenName");
        fieldMap.put("followers_count", "followersCount");
        fieldMap.put("profile_image_url", "profileImageUrl");
        fieldMap.put("friends_count", "friendsCount");
        fieldMap.put("favourites_count", "favouritesCount");
        fieldMap.put("geo_enabled", "geoEnabled");
        fieldMap.put("listed_count", "listedCount");
        fieldMap.put("profile_background_image_url", "profileBackgroundImageUrl");
        fieldMap.put("protected_user", "protectedUser");
        fieldMap.put("statuses_count", "statusesCount");
        fieldMap.put("time_zone", "timeZone");
        fieldMap.put("contributors_enabled", "contributorsEnabled");
        fieldMap.put("utc_offset", "utcOffset");
        fieldMap.put("created_at", "createdAt");
        fieldMap.put("in_reply_to_status_id", "inReplyToStatusId");
        fieldMap.put("in_reply_to_userid", "inReplyToUserId");
    }
    
    public void init() {
        driver = new Driver(clientAdapterHost, clientAdapterPort);
        try {
            boolean init = driver.init();
            init &= driver.connect();
            if (!init) {
                throw new RuntimeException("Failed to initialize client adapter driver");
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        
        (new Thread(new Dequeuer())).start();
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUrlString(String urlString) {
        this.urlString = urlString;
    }

    public void setMaxBackoffTime(long maxBackoffTime) {
        this.maxBackoffTime = maxBackoffTime;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public void setClientAdapterHost(String clientAdapterHost) {
        this.clientAdapterHost = clientAdapterHost;
    }

    public void setClientAdapterPort(int clientAdapterPort) {
        this.clientAdapterPort = clientAdapterPort;
    }

    public void run() {
        long backoffTime = 1000;
        while (!Thread.interrupted()) {
            try {
                connectAndRead();
            } catch (Exception e) {
                Logger.getLogger("s4").error("Exception reading feed", e);
                try {
                    Thread.sleep(backoffTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                backoffTime = backoffTime * 2;
                if (backoffTime > maxBackoffTime) {
                    backoffTime = maxBackoffTime;
                }
            }
        }
    }

    public void connectAndRead() throws Exception {
        URL url = new URL(urlString);

        URLConnection connection = url.openConnection();
        String userPassword = userid + ":" + password;
        String encoded = EncodingUtil.getAsciiString(Base64.encodeBase64(EncodingUtil.getAsciiBytes(userPassword)));
        connection.setRequestProperty("Authorization", "Basic " + encoded);
        connection.connect();

        InputStream is = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);

        String inputLine = null;
        while ((inputLine = br.readLine()) != null) {
            if (inputLine.trim().length() == 0) {
                blankCount++;
                continue;
            }
            messageCount++;
            messageQueue.add(inputLine);
        }
    }
    
    class Dequeuer implements Runnable {
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    String message = messageQueue.take();
                    JSONObject messageJSON = new JSONObject(message);

                    // ignore delete records for now
                    if (messageJSON.has("delete")) {
                        continue;
                    }

                    // create a copy with some renamed fields
                    JSONObject statusJSON = getStatus(messageJSON);
                    Message m = new Message(streamName, "io.s4.example.twittertopiccount.Status", statusJSON.toString());
                    driver.send(m);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } catch (JSONException je) {
                    je.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }  
        }
        
        public JSONObject getStatus(JSONObject origStatusJSON) {
            try {
                if (origStatusJSON == null || origStatusJSON.equals(JSONObject.NULL)) {
                    return null;
                }
                
                JSONObject statusJSON = new JSONObject();
                copyFields(origStatusJSON, statusJSON, fieldMap);
                
                JSONObject origUserJSON = origStatusJSON.getJSONObject("user");
                JSONObject userJSON = new JSONObject();
                copyFields(origUserJSON, userJSON, fieldMap);
                statusJSON.put("user", userJSON);
 
                return statusJSON;    
            } catch (Exception e) {
                Logger.getLogger("s4").error(e);
            }
            
            return new JSONObject();
        }
        
        public void copyFields(JSONObject from, JSONObject to, Map<String,String> fieldMap) 
            throws JSONException {
            for (Iterator<?> it = from.keys(); it.hasNext(); ) {
                String fieldName = (String) it.next();
                String adjustedFieldName = fieldName;
                String proposedFieldName = null;
                if ((proposedFieldName = fieldMap.get(fieldName)) != null) {
                    adjustedFieldName = proposedFieldName;
                }
                to.put(adjustedFieldName, from.get(fieldName));
            }        
        }        
    }

 
       
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("a", "adapter_address", true, "Adapter address");
        options.addOption("u", "url_string", true, "URL string");
        CommandLineParser parser = new GnuParser();

        CommandLine line = null;
        try {
            // parse the command line arguments
            line = parser.parse(options, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            System.exit(1);
        }
        
        List<?> loArgs = line.getArgList();

        String clientAdapterAddress = null;
        String clientAdapterHost = null;
        int clientAdapterPort = -1;
        if (line.hasOption("a")) {
            clientAdapterAddress = line.getOptionValue("a");
            String[] parts = clientAdapterAddress.split(":");
            if (parts.length != 2) {
                System.err.println("Bad adapter address specified "
                        + clientAdapterAddress);
                System.exit(1);
            }
            clientAdapterHost = parts[0];
            
            try {
                clientAdapterPort = Integer.parseInt(parts[1]);
            }
            catch (NumberFormatException nfe) {
                System.err.println("Bad adapter address specified "
                        + clientAdapterAddress);
                System.exit(1);                
            }
        }
        
        String urlString = null;
        if (line.hasOption("u")) {
            urlString = line.getOptionValue("u");
        }

        if (loArgs.size() < 1) {
            System.err.println("No userid specified");
            System.exit(1);
        }
        
        if (loArgs.size() < 2) {
            System.err.println("No password specified");
            System.exit(1);
        }

        TwitterFeedListener tfl = new TwitterFeedListener();
        tfl.setUserid((String)loArgs.get(0));
        tfl.setPassword((String)loArgs.get(1));
        if (clientAdapterAddress != null) {
            tfl.setClientAdapterHost(clientAdapterHost);
            tfl.setClientAdapterPort(clientAdapterPort);
        }
        if (urlString != null) {
            tfl.setUrlString(urlString);
        }

        tfl.init();
        tfl.run();
    }
}
