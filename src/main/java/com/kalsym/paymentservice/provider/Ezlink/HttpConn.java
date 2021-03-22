/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider.Ezlink;

import com.kalsym.paymentservice.utils.LogUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author user
 */
public class HttpConn {
    
     private static HttpResult makeHttpConnection(String httpMethod, String refId, String wsUrl, HashMap httpHeader, String requestBody, int connectTimeout, int waitTimeout) {
        String loglocation = "HttpConn";
        HttpResult result = new HttpResult();
        try {
            LogUtil.info(refId, loglocation, "Sending Request to :" + wsUrl, "");
            URL url = new URL(wsUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(connectTimeout);
            con.setReadTimeout(waitTimeout);
            if (httpMethod.equalsIgnoreCase("post")) {
                con.setRequestMethod("POST");
            } else {
                con.setRequestMethod("GET");
            }
            con.setDoOutput(true);
            
            LogUtil.info(refId, loglocation, "Set HTTP Header","");
            Iterator it = httpHeader.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                LogUtil.info(refId, loglocation, (String)pair.getKey() + " = " + (String)pair.getValue(), "");
                con.setRequestProperty((String)pair.getKey(), (String)pair.getValue());
                it.remove(); // avoids a ConcurrentModificationException
            }
            con.connect();

            if (requestBody!=null) {
                //for post paramters in JSON Format
                OutputStream os = con.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");

                LogUtil.info(refId, loglocation, "Request JSON -> "+requestBody,"");
                osw.write(requestBody);
                osw.flush();
                osw.close();
            }
            
            int responseCode = con.getResponseCode();
            result.httpResponseCode = responseCode;
            LogUtil.info(refId, loglocation, "HTTP Response code:" + responseCode,"");
            
            ///get all headers
            LogUtil.info(refId, loglocation, "Extract HTTP Headers","");
            Map<String, List<String>> map = con.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                String key = entry.getKey(); 
                LogUtil.info(refId,loglocation,""+key+" : "+entry.getValue(),"");
            }
            
            BufferedReader in=null;
            if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
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
            LogUtil.info(refId,loglocation,"Response JSON <- "+httpMsgResp.toString(),"");
            
            result.responseString = httpMsgResp.toString();
        } catch (SocketTimeoutException ex) {
            if (ex.getMessage().equals("Read timed out")) {
                result.resultCode = -2;
                result.responseString = ex.getMessage();
                LogUtil.error(refId, loglocation, "[" + refId + "] Exception : " + ex.getMessage(), "", ex);
            } else {
                result.resultCode = -1;
                result.responseString = ex.getMessage();
                LogUtil.error(refId, loglocation, "[" + refId + "] Exception : " + ex.getMessage(), "", ex);
            }
        } catch (Exception ex) {
            LogUtil.error(refId,loglocation,"Exception : "+ex.getMessage(),"",ex);
            result.resultCode = -1;
            result.responseString = ex.getMessage();
        }
        return result;
    }
}
