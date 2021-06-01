package com.kalsym.paymentservice.provider.SenangPay;

import com.kalsym.paymentservice.utils.LogUtil;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HttpConnection {

    public static HttpResult SendHttpsRequest(String httpMethod, String refId, String targetUrl, HashMap httpHeader, String requestBody, int connectTimeout, int waitTimeout) {
        HttpResult response = new HttpResult();
        String loglocation = "HttpConnection";

        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }};

            // Ignore differences between given hostname and certificate hostname
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAllCerts, new SecureRandom());

            LogUtil.info(refId, loglocation, "Sending Request to :" + targetUrl, "");
            URL url = new URL(targetUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setSSLSocketFactory(sc.getSocketFactory());
            con.setHostnameVerifier(hv);
            con.setConnectTimeout(connectTimeout);
            con.setReadTimeout(waitTimeout);
            con.setRequestMethod(httpMethod);
            con.setDoOutput(true);


            //Set HTTP Headers
            LogUtil.info(refId, loglocation, "Set HTTP Header", "");
            Iterator it = httpHeader.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                LogUtil.info(refId, loglocation, (String) pair.getKey() + " = " + (String) pair.getValue(), "");
                con.setRequestProperty((String) pair.getKey(), (String) pair.getValue());
                it.remove(); // avoids a ConcurrentModificationException
            }

            con.connect();

//            if (requestBody != null) {
//                //for post paramters in JSON Format
//                OutputStream os = con.getOutputStream();
//                OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
//
//                LogUtil.info(refId, loglocation, "Request JSON :" + requestBody, "");
//                osw.write(requestBody);
//                osw.flush();
//                osw.close();
//            }

            int responseCode = con.getResponseCode();
            LogUtil.info(refId, loglocation, "HTTP Response code:" + responseCode, "");
            response.httpResponseCode = responseCode;

            BufferedReader in;
            if (responseCode < HttpsURLConnection.HTTP_BAD_REQUEST) {
                in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
            }
            String inputLine;
            StringBuilder httpMsgResp = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                httpMsgResp.append(inputLine);
            }
            in.close();

            LogUtil.info(refId, loglocation, "Response of topup :" + httpMsgResp.toString(), "");

            response.resultCode = 0;
            response.responseString = httpMsgResp.toString();

        } catch (SocketTimeoutException ex) {
            if (ex.getMessage().equals("Read timed out")) {
                response.resultCode = -2;
                response.responseString = ex.getMessage();
                LogUtil.error(refId, loglocation, "Exception : " + ex.getMessage(), "", ex);
            } else {
                response.resultCode = -1;
                response.responseString = ex.getMessage();
                LogUtil.error(refId, loglocation, "Exception : " + ex.getMessage(), "", ex);
            }
        } catch (Exception ex) {
            //exception occur
            response.resultCode = -1;
            response.responseString = ex.getMessage();
            LogUtil.error(refId, loglocation, "Exception during send request : ", "", ex);
        }

        return response;
    }


}
