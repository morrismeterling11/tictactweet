
package com.klinker.android.twitter_l.utils.api_helper;
/*
 * Copyright 2014 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.util.Log;
import com.klinker.android.twitter_l.APIKeys;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;

/**
 * This is just a simple helper class to facilitate things with TwitLonger's 2.0 APIs
 * It is based off the assertion that you use Twitter4j, but could be easily adapted if you don't
 *
 * @author Luke Klinker (along with help from Twitter4j)
 */
public class TwitLongerHelper extends APIHelper {

    public static final String TWITLONGER_API_KEY = APIKeys.TWITLONGER_API_KEY;
    public static final String POST_URL = "http://api.twitlonger.com/2/posts";
    public static final String PUT_URL = "http://api.twitlonger.com/2/posts/"; // will add the id to the end of this later

    public String tweetText;
    public long replyToId;
    public long replyToStatusId = 0;
    public String replyToScreenname;
    public GeoLocation location;
    public Context context;

    public Twitter twitter;

    /**
     * Used for a normal tweet, not a reply
     * @param tweetText the text of the tweet that you want to post
     */
	public TwitLongerHelper(String tweetText, Twitter twitter, Context context) {
        this.tweetText = tweetText;
        this.replyToId = 0;
        this.replyToScreenname = null;

        this.twitter = twitter;
        this.context = context;
    }

    /**
     * Used when repling to a user and you have their id number
     * @param tweetText the text of the tweet that you want to post
     * @param replyToId the id of the user your tweet is replying to
     */
    public TwitLongerHelper(String tweetText, long replyToId, Twitter twitter, Context context) {
        this.tweetText = tweetText;
        this.replyToId = replyToId;
        this.replyToScreenname = null;

        this.twitter = twitter;
        this.context = context;
    }

    /**
     * Used when repling to a user and you have their id number
     * @param tweetText the text of the tweet that you want to post
     * @param replyToScreenname the screenname of the user you are replying to
     */
    public TwitLongerHelper(String tweetText, String replyToScreenname, Twitter twitter, Context c) {
        this.tweetText = tweetText;
        this.replyToScreenname = replyToScreenname;
        this.replyToId = 0;

        this.twitter = twitter;
        this.context = c;
    }

    /**
     * Sets the tweet id if it is replying to another users tweet
     * @param replyToStatusId
     */
    public void setInReplyToStatusId(long replyToStatusId) {
        this.replyToStatusId = replyToStatusId;
    }

    public void setLocation(GeoLocation location) {
        this.location = location;
    }


    /**
     * posts the status onto Twitlonger, it then posts the shortened status (with link) to the user's twitter and updates the status on twitlonger
     * to include the posted status's id.
     *
     * @return id of the status that was posted to twitter
     */
    public long createPost() {
        TwitLongerStatus status = postToTwitLonger();
        long statusId;
        try {
            Status postedStatus;
            StatusUpdate update = new StatusUpdate(status.getText());
            if (replyToStatusId != 0) {
                update.setInReplyToStatusId(replyToStatusId);
            }

            if (location != null) {
                update.setLocation(location);
            }

            postedStatus = twitter.updateStatus(update);

            statusId = postedStatus.getId();
            updateTwitlonger(status, statusId);
        } catch (Throwable e) {
            e.printStackTrace();
            statusId = 0;
        }

        // if zero, then it failed
        return statusId;
    }

    /**
     * Posts the status to twitlonger
     * @return returns an object containing the shortened text and the id for the twitlonger url
     */
    public TwitLongerStatus postToTwitLonger() {
        try {
            Request request = new Request.Builder()
                    .url(POST_URL)
                    .addHeader("X-API-KEY", TWITLONGER_API_KEY)
                    .addHeader("X-Auth-Service-Provider", SERVICE_PROVIDER)
                    .addHeader("X-Verify-Credentials-Authorization", getAuthrityHeader(twitter, context))
                    .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;  charset=utf-8"), buildUrlParams().getBytes("UTF8")))
                    .build();

            Response response = new OkHttpClient.Builder().build().newCall(request).execute();
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.body().byteStream()));

            String line;
            String content = "";
            String id = "";

            StringBuilder builder = new StringBuilder();

            while ((line = rd.readLine()) != null) {
                builder.append(line);
            }

            Log.v("twitlonger", builder.toString());

            try {
                // there is only going to be one thing returned ever
                JSONObject jsonObject = new JSONObject(builder.toString());
                content = jsonObject.getString("tweet_content");
                id = jsonObject.getString("id");
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.v("talon_twitlonger", "content: " + content);
            Log.v("talon_twitlonger", "id: " + id);
            return new TwitLongerStatus(content, id);

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private String buildUrlParams() {
        try {
            String url = "content=" + URLEncoder.encode(tweetText, "UTF-8") + "&";
            if (replyToId != 0) {
                url += "reply_to_id=" + String.valueOf(replyToId);
            } else if (replyToScreenname != null) {
                url += "reply_to_screen_name=" + replyToScreenname;
            }

            return url;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Updates the status on twitlonger to include the tweet id from Twitter.
     * Helpful for threading.
     * @param status Object with the shortened text and the id
     * @param tweetId tweet id of the status posted to twitter
     * @return true if the update is sucessful
     */
    public boolean updateTwitlonger(TwitLongerStatus status, long tweetId) {
        try {
            Request request = new Request.Builder()
                    .url(PUT_URL + status.getId())
                    .addHeader("X-API-KEY", TWITLONGER_API_KEY)
                    .addHeader("X-Auth-Service-Provider", SERVICE_PROVIDER)
                    .addHeader("X-Verify-Credentials-Authorization", getAuthrityHeader(twitter, context))
                    .put(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;  charset=utf-8"), ("twitter_status_id=" + tweetId).getBytes("UTF8")))
                    .build();

            Response response = new OkHttpClient.Builder().build().newCall(request).execute();
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.body().byteStream()));

            if (rd.readLine() != null) {
                Log.v("twitlonger", "updated the status successfully");
                return true;
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    class TwitLongerStatus {
        private String text;
        private String id;

        public TwitLongerStatus(String text, String id) {
            this.text = text;
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }
    }

}
