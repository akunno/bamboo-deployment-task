package com.atlassian.bamboo.plugins.deployment.releaser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.common.base.Joiner;

public class AtlassianHttpService {

    public enum HttpMethod {
        POST, GET
    };

    private List<String> authorisationCookies;
    private String xhrToken;
    private String jSessionId;

    public String makeRequest(URL requestUrl) throws IOException, Exception {
        return this.makeRequest(requestUrl, HttpMethod.GET, new HashMap<String, String>());
    }

    public String makeRequest(URL requestUrl, HttpMethod method, Map<String, String> parameters) throws IOException, Exception {

        // Create a trust manager that does not validate certificate chains
        // This is a DODGY hack until such time that we can get the code (bottom
        // of this file) working
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }
        }};

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            // bamboo_dependency_parent_0
        } catch (Exception e) {
        }

        URL sessionRequestUrl = null;
        if (jSessionId != null && jSessionId.length() > 0) {
            sessionRequestUrl = new URL(requestUrl + "?jsessionid=" + jSessionId);
        } else {
            sessionRequestUrl = new URL(requestUrl + "");
        }

        HttpURLConnection connection = (HttpURLConnection) sessionRequestUrl.openConnection();
        connection.setInstanceFollowRedirects(false);
        HttpURLConnection.setFollowRedirects(false);
        connection.setRequestMethod(method.name());
        System.out.println("Sending data via " + method + " to URL " + sessionRequestUrl.toString());
        connection.setRequestProperty("X-Requested-With", "Curl");
        connection.setRequestProperty("charset", "utf-8");

        if (authorisationCookies != null) {
            for (String cookie : authorisationCookies) {
                System.out.println("        Put Cookie: " + cookie);
                connection.setRequestProperty("Cookie", cookie);
            }
        }

        if (method.equals(HttpMethod.POST)) {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (xhrToken != null && xhrToken.length() > 0) {
                parameters.put("atl_token", xhrToken);
                xhrToken = "";
            } else {
                parameters.put("atl_token", "");
                parameters.put("atl_token_source", "js");
            }
            List<String> postData = new ArrayList<String>();
            List<String> postDataKeysOnly = new ArrayList<String>();
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                postDataKeysOnly.add(URLEncoder.encode(entry.getKey(), "UTF-8"));
                postData.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "=" + URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            String postDataJoined = Joiner.on("&").join(postData);
            // String postDataKeysOnlyJoined = Joiner.on("&").join(postDataKeysOnly);

            // System.out.println("        Params[" + postDataKeysOnlyJoined + "]");
            // System.out.println("Sending data: " + postDataJoined);

            connection.setDoOutput(true);
            OutputStreamWriter postDataWriter = new OutputStreamWriter(connection.getOutputStream());
            postDataWriter.write(postDataJoined);
            postDataWriter.flush();
            postDataWriter.close();
        }

        connection = processRedirect(connection);

        BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        // put output stream into a string
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = inputStreamReader.readLine()) != null) {
            stringBuilder.append(line + "\n");
        }
        inputStreamReader.close();

        String result = stringBuilder.toString();
        // System.err.println(result);

        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                System.out.println("        Got Cookie: " + cookie);
            }
            authorisationCookies = cookies;
        }
        xhrToken = extractTokenFromPage(result);
        jSessionId = extractSessionIdFromPage(result);

        connection.disconnect();

        return result;
    }

    private String extractSessionIdFromPage(String page) throws Exception {
        // <form id="loginForm" name="loginForm"
        // action="/bamboo/userlogin.action;jsessionid=5C0EACC299C681B016FE2954A1901721"
        // method="post" class="aui">
        Pattern p = Pattern.compile("action=\"[a-zA-Z0-9/]*/userlogin.action;jsessionid=(.*?)\"");
        Matcher m = p.matcher(page);
        if (m.find()) {
            String token = m.group(1);
            return token;
        }
        return "";// throw new
                  // MissingArgumentException("ATL token is missing from page");
    }

    private String extractTokenFromPage(String page) throws Exception {
        // <input type="hidden" name="atl_token"
        // value="d2ac1d21aa552566c108a0a1ed284fd17bf05c04" />
        Pattern p = Pattern.compile("type=\"hidden\" name=\"atl_token\" value=\"(.*?)\"");
        Matcher m = p.matcher(page);
        if (m.find()) {
            String token = m.group(1);
            return token;
        }
        return "";// throw new
                  // MissingArgumentException("ATL token is missing from page");
    }

    protected HttpURLConnection processRedirect(HttpURLConnection conn) throws MalformedURLException, IOException {
        boolean redirect = false;

        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER)
                redirect = true;
        }

        System.out.println("Response Code ... " + status);

        if (redirect) {
            // get redirect url from "location" header field
            String newUrl = conn.getHeaderField("Location");
            authorisationCookies = conn.getHeaderFields().get("Set-Cookie");
            System.err.println("Received: " + newUrl);
            // open the new connnection again
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
            conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
            conn.addRequestProperty("User-Agent", "Mozilla");
            conn.addRequestProperty("Referer", "google.com");

            if (authorisationCookies != null) {
                for (String cookie : authorisationCookies) {
                    System.out.println("        Put Cookie: " + cookie);
                    conn.setRequestProperty("Cookie", cookie);
                }
            }
            System.out.println("Redirect to URL : " + newUrl);
        }
        return conn;
    }

}
