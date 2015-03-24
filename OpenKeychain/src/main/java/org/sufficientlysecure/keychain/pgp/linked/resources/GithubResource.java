package org.sufficientlysecure.keychain.pgp.linked.resources;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;

import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.operations.results.OperationResult.LogType;
import org.sufficientlysecure.keychain.operations.results.OperationResult.OperationLog;
import org.sufficientlysecure.keychain.pgp.linked.LinkedCookieResource;
import org.sufficientlysecure.keychain.util.Log;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GithubResource extends LinkedCookieResource {

    final String mHandle;
    final String mGistId;

    GithubResource(Set<String> flags, HashMap<String,String> params, URI uri,
            String handle, String gistId) {
        super(flags, params, uri);

        mHandle = handle;
        mGistId = gistId;
    }

    public static String generate(Context context, byte[] fingerprint) {
        String cookie = LinkedCookieResource.generate(context, fingerprint);

        return String.format(context.getResources().getString(R.string.linked_id_github_text), cookie);
    }

    @Override
    protected String fetchResource (OperationLog log, int indent) {

        log.add(LogType.MSG_LV_FETCH, indent, mSubUri.toString());
        indent += 1;

        try {

            HttpGet httpGet = new HttpGet("https://api.github.com/gists/" + mGistId);
            httpGet.setHeader("User-Agent", "OpenKeychain");

            String response = getResponseBody(httpGet);

            JSONObject obj = new JSONObject(response);

            JSONObject owner = obj.getJSONObject("owner");
            if (!mHandle.equals(owner.getString("login"))) {
                log.add(LogType.MSG_LV_FETCH_ERROR_FORMAT, indent);
                return null;
            }

            JSONObject files = obj.getJSONObject("files");
            Iterator<String> it = files.keys();
            if (it.hasNext()) {
                // TODO can there be multiple candidates?
                JSONObject file = files.getJSONObject(it.next());
                return file.getString("content");
            }

        } catch (HttpStatusException e) {
            // log verbose output to logcat
            Log.e(Constants.TAG, "http error (" + e.getStatus() + "): " + e.getReason());
            log.add(LogType.MSG_LV_FETCH_ERROR, indent, Integer.toString(e.getStatus()));
        } catch (MalformedURLException e) {
            log.add(LogType.MSG_LV_FETCH_ERROR_URL, indent);
        } catch (IOException e) {
            Log.e(Constants.TAG, "io error", e);
            log.add(LogType.MSG_LV_FETCH_ERROR_IO, indent);
        } catch (JSONException e) {
            Log.e(Constants.TAG, "json error", e);
            log.add(LogType.MSG_LV_FETCH_ERROR_FORMAT, indent);
        }
        return null;

    }

    public static GithubResource searchInGithubStream(String screenName, String needle) {

        // narrow the needle down to important part
        Matcher matcher = magicPattern.matcher(needle);
        if (!matcher.find()) {
            Log.e(Constants.TAG, "needle didn't contain cookie!");
            return null;
        }
        needle = matcher.group();

        try {

            JSONArray array; {
                HttpGet httpGet =
                        new HttpGet("https://api.github.com/users/" + screenName + "/gists");
                httpGet.setHeader("Content-Type", "application/json");
                httpGet.setHeader("User-Agent", "OpenKeychain");

                String response = getResponseBody(httpGet);
                array = new JSONArray(response);
            }

            for (int i = 0, j = Math.min(array.length(), 5); i < j; i++) {
                JSONObject obj = array.getJSONObject(i);

                JSONObject files = obj.getJSONObject("files");
                Iterator<String> it = files.keys();
                if (it.hasNext()) {

                    JSONObject file = files.getJSONObject(it.next());
                    String type = file.getString("type");
                    if (!"text/plain".equals(type)) {
                        continue;
                    }
                    String id = obj.getString("id");
                    HttpGet httpGet = new HttpGet("https://api.github.com/gists/" + id);
                    httpGet.setHeader("User-Agent", "OpenKeychain");

                    JSONObject gistObj = new JSONObject(getResponseBody(httpGet));
                    JSONObject gistFiles = gistObj.getJSONObject("files");
                    Iterator<String> gistIt = gistFiles.keys();
                    if (!gistIt.hasNext()) {
                        continue;
                    }
                    // TODO can there be multiple candidates?
                    JSONObject gistFile = gistFiles.getJSONObject(gistIt.next());
                    String content = gistFile.getString("content");
                    if (!content.contains(needle)) {
                        continue;
                    }

                    URI uri = URI.create("https://gist.github.com/" + screenName + "/" + id);
                    return create(uri);
                }
            }

            // update the results with the body of the response
            return null;
        } catch (JSONException | HttpStatusException | IOException e) {
            Log.e(Constants.TAG, "exception parsing stream", e);
        }

        return null;
    }

    public static GithubResource create(URI uri) {
        return create(new HashSet<String>(), new HashMap<String,String>(), uri);
    }

    public static GithubResource create(Set<String> flags, HashMap<String,String> params, URI uri) {

        // no params or flags
        if (!flags.isEmpty() || !params.isEmpty()) {
            return null;
        }

        Pattern p = Pattern.compile("https://gist\\.github\\.com/([a-zA-Z0-9_]+)/([0-9a-f]+)");
        Matcher match = p.matcher(uri.toString());
        if (!match.matches()) {
            return null;
        }
        String handle = match.group(1);
        String gistId = match.group(2);

        return new GithubResource(flags, params, uri, handle, gistId);

    }


    @Override
    public @DrawableRes
    int getDisplayIcon() {
        return R.drawable.github;
    }

    @Override
    public @StringRes
    int getVerifiedText() {
        return R.string.linked_verified_github;
    }

    @Override
    public String getDisplayTitle(Context context) {
        return "Github";
    }

    @Override
    public String getDisplayComment(Context context) {
        return mHandle;
    }

    @Override
    public boolean isViewable() {
        return true;
    }

    @Override
    public Intent getViewIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(mSubUri.toString()));
        return intent;
    }
}