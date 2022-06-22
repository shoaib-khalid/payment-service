package com.kalsym.paymentservice.controllers;

import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.HttpReponse;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SpCallbackResult;
import com.kalsym.paymentservice.repositories.*;
import com.kalsym.paymentservice.service.OrderPaymentService;
import com.kalsym.paymentservice.service.Response.OrderConfirm;
import com.kalsym.paymentservice.service.Response.StoreDetails;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import com.kalsym.paymentservice.utils.StringUtility;
//import okhttp3.RequestBody;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.apache.http.NameValuePair;
import org.springframework.web.client.RestTemplate;


import javax.net.ssl.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.websocket.server.PathParam;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.io.*;

/**
 * @author Sarosh
 */
@RestController()
@RequestMapping("/payments")
public class PaymentsController {

    @Autowired
    ProviderRatePlanRepository providerRatePlanRepository;

    @Autowired
    ProviderConfigurationRepository providerConfigurationRepository;

    @Autowired
    ProviderRepository providerRepository;

    @Autowired
    PaymentOrdersRepository paymentOrdersRepository;

    @Autowired
    ProviderIpRepository providerIpRepository;

    @Autowired
    OrderPaymentService paymentService;


    @Value("${paymentRedirectUrl}")
    String paymentRedirectUrl;

    @PostMapping(path = {"/makePayment"}, name = "payments-make-payment")
    public ResponseEntity<HttpReponse> makePayment(HttpServletRequest request,
                                                   @Valid @RequestBody PaymentRequest paymentRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "", "");
        paymentRequest.setPaymentAmount(null);

        //generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("PY");
        paymentRequest.setSystemTransactionId(systemTransactionId);

        OrderConfirm res = paymentService.getOrderById(paymentRequest.getTransactionId());
        LogUtil.info(systemTransactionId, location, "Order Service Return :   ", res.getTotal().toString());
        StoreDetails storeDetails = paymentService.getStore(res.getStoreId());

        paymentRequest.setRegionCountryId(storeDetails.getRegionCountryId());
        paymentRequest.setPaymentAmount(res.getTotal());
        LogUtil.info(systemTransactionId, location, "Payment Amount  ", paymentRequest.getPaymentAmount().toString());


        LogUtil.info(systemTransactionId, location, "PaymentOrder Id ", paymentRequest.getTransactionId());

        ProcessRequest process = new ProcessRequest(systemTransactionId, paymentRequest, providerRatePlanRepository, providerConfigurationRepository, providerRepository);
        ProcessResult processResult = process.MakePayment();
        LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode + " isSuccess:" + processResult.isSuccess, "");

        if (processResult.isSuccess) {
            //successfully submit order to provider
            //store result in delivery order
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setCustomerId(paymentRequest.getCustomerId());
            paymentOrder.setClientTransactionId(paymentRequest.getTransactionId());
            paymentOrder.setSystemTransactionId(systemTransactionId);
            paymentOrder.setStatus("PENDING");
            paymentOrder.setProductCode(paymentRequest.getProductCode());

            MakePaymentResult paymentOrderResult = (MakePaymentResult) processResult.returnObject;
            PaymentOrder orderCreated = paymentOrderResult.orderCreated;
            paymentOrder.setCreatedDate(orderCreated.getCreatedDate());
            paymentOrder.setSpId(paymentOrderResult.providerId);

            paymentOrder.setHash(paymentOrderResult.hash);
            paymentOrder.setHashDate(paymentOrderResult.hashDate);
            paymentOrder.setPaymentAmount(paymentRequest.getPaymentAmount());

            LogUtil.info(systemTransactionId, location, "PaymentOrder ", paymentOrder.toString());

            paymentOrdersRepository.save(paymentOrder);

            response.setSuccessStatus(HttpStatus.OK);
            response.setData(processResult.returnObject);
            LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } else {
            //fail to make payment
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    //TODO : Proper way callback in testing
    @GetMapping(path = {"/payment-redirect"}, name = "payments-sp-callback")
    public ResponseEntity<HttpReponse> call(HttpServletRequest request,
                                            @RequestParam(name = "name", required = false, defaultValue = "") String name,
                                            @RequestParam(name = "email", required = false, defaultValue = "") String email,
                                            @RequestParam(name = "phone", required = false, defaultValue = "") String phone,
                                            @RequestParam(name = "transaction_amount", required = false, defaultValue = "") String transaction_amount,
                                            @RequestParam(name = "status_id", required = false, defaultValue = "") int status_id,
                                            @RequestParam(name = "order_id", required = false, defaultValue = "") String order_id,
                                            @RequestParam(name = "transaction_id", required = false, defaultValue = "") String transaction_id,
                                            @RequestParam(name = "hash", required = false, defaultValue = "") String hash,
                                            @RequestParam(name = "basket_id", required = false, defaultValue = "") String basket_id,
                                            @RequestParam(name = "Rdv_Message_Key", required = false, defaultValue = "") String Rdv_Message_Key,
                                            @RequestParam(name = "PaymentType", required = false, defaultValue = "") String PaymentType,
                                            @RequestParam(name = "PaymentName", required = false, defaultValue = "") String PaymentName,
                                            @RequestParam(name = "validation_hash", defaultValue = "") String validation_hash,
                                            @RequestParam(name = "err_code", required = false) String err_code,
                                            @RequestParam(name = "order_date", required = false, defaultValue = "") String order_date,
                                            @RequestParam(name = "payment_channel", required = false, defaultValue = "") String payment_channel,
                                            @RequestParam(name = "err_msg", required = false, defaultValue = "") String err_msg,
                                            @RequestParam(name = "msg", required = false, defaultValue = "") String msg) {

        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());
        System.err.println("NAME " + validation_hash);

        LogUtil.info(logprefix, location, "receive callback from Provider", "");
        //generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("CB");
        String IP = request.getRemoteAddr();

        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(basket_id);


        JsonObject requestBody = new JsonObject();
        System.err.println("Order ID FROM URL " + order_id);
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("phone", phone);
        requestBody.addProperty("status_id", String.valueOf(status_id));
        requestBody.addProperty("order_id", order_id);
        requestBody.addProperty("basket_id", basket_id);
        requestBody.addProperty("transaction_id", transaction_id);
        requestBody.addProperty("payment_channel", payment_channel);


        if (err_code.equals("000")) {
            requestBody.addProperty("transaction_amount", transaction_amount);
            requestBody.addProperty("Rdv_Message_Key", Rdv_Message_Key);
            requestBody.addProperty("PaymentType", PaymentType);
            requestBody.addProperty("PaymentName", PaymentName);
            requestBody.addProperty("validation_hash", validation_hash);
            requestBody.addProperty("err_code", err_code);
            requestBody.addProperty("msg", msg);
            requestBody.addProperty("hash", hash);
            requestBody.addProperty("hashDate", order.getHashDate());
            requestBody.addProperty("systemHash", order.getHash());
            requestBody.addProperty("amount", order.getPaymentAmount());
        } else {
            requestBody.addProperty("err_msg", err_msg);
            requestBody.addProperty("err_code", err_code);
            requestBody.addProperty("order_date", order_date);
        }

//        LogUtil.info(logprefix, location, "IP:" + IP, order.getClientTransactionId());
//        LogUtil.info(logprefix, location, "IP:" + IP, order.getClientTransactionId());
//        LogUtil.info(logprefix, location, "IP:" + IP, providerIpRepository.toString());
        ProcessRequest process = new ProcessRequest(systemTransactionId, requestBody, providerRatePlanRepository, providerConfigurationRepository, providerRepository);
        ProcessResult processResult = process.ProcessCallback(IP, providerIpRepository, order.getSpId());
        LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode, "");

        if (processResult.resultCode == 0) {
            try {

                OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_CONFIRMED", "", msg);
            } catch (Exception ex) {
                LogUtil.info(systemTransactionId, location, "Payment Update Failed: ", ex.getMessage());

            }
            //update order status in db
            SpCallbackResult spCallbackResult = (SpCallbackResult) processResult.returnObject;
            String spOrderId = spCallbackResult.orderId;
            String status = spCallbackResult.status;
            String paymentChanel = spCallbackResult.paymentChanel;
            int statusId = spCallbackResult.statusId;
            String spErrorCode = spCallbackResult.spErrorCode;
            String paymentTransactionId = spCallbackResult.paymentTransactionId;
            String clientTransactionId = "";
            PaymentOrder deliveryOrder = paymentOrdersRepository.findBySystemTransactionId(paymentTransactionId);
            if (deliveryOrder != null) {
                clientTransactionId = deliveryOrder.getClientTransactionId();
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                deliveryOrder.setStatus(status);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(spOrderId);
                paymentOrdersRepository.save(deliveryOrder);
            } else {
                LogUtil.info(systemTransactionId, location, "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");

            }
            try {
                //send redirect to Thank You page
                String url = paymentRedirectUrl + "name=" + name + "&email=" + email + "&phone=" + phone + "&amount=" + transaction_amount + "&hash=&status_id=" + statusId + "&order_id=" + spOrderId + "&transaction_id=" + paymentTransactionId + "&msg=" + status + "&payment_channel=" + paymentChanel;
                LogUtil.info(systemTransactionId, location, "Redirect to url:" + url + " with " + HttpStatus.SEE_OTHER, "");
                URI uri = new URI(url);
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setLocation(uri);
                //use TEMPORARY_REDIRECT to forward request from payment gateway to frond-end
                //return new ResponseEntity<>(httpHeaders, HttpStatus.TEMPORARY_REDIRECT);
                return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);
            } catch (Exception ex) {
                LogUtil.error(systemTransactionId, location, "Redirecting error", "", ex);
                response.setSuccessStatus(HttpStatus.OK);
                response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        } else {
            try {
                OrderConfirm res = paymentService.updateStatus(order_id, "FAILED", "", msg);
            } catch (Exception ex) {
                LogUtil.info(systemTransactionId, location, "Payment Update Failed: ", ex.getMessage());

            }

            SpCallbackResult spCallbackResult = (SpCallbackResult) processResult.returnObject;
            String spOrderId = spCallbackResult.orderId;
            String status = spCallbackResult.status;
            String paymentChanel = spCallbackResult.paymentChanel;
            int statusId = spCallbackResult.statusId;
            String spErrorCode = spCallbackResult.spErrorCode;
            String paymentTransactionId = spCallbackResult.paymentTransactionId;
            String clientTransactionId = "";
            PaymentOrder deliveryOrder = paymentOrdersRepository.findBySystemTransactionId(paymentTransactionId);
            if (deliveryOrder != null) {
                clientTransactionId = deliveryOrder.getClientTransactionId();
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                deliveryOrder.setStatus(status);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(spOrderId);
                paymentOrdersRepository.save(deliveryOrder);
            } else {
                LogUtil.info(systemTransactionId, location, "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");

            }
            try {
                //send redirect to Thank You page
                String url = paymentRedirectUrl + "name=" + name + "&email=" + email + "&phone=" + phone + "&amount=" + transaction_amount + "&hash=&status_id=" + statusId + "&order_id=" + spOrderId + "&transaction_id=" + paymentTransactionId + "&msg=" + status + "&payment_channel=" + paymentChanel;
                LogUtil.info(systemTransactionId, location, "Redirect to url:" + url + " with " + HttpStatus.SEE_OTHER, "");
                URI uri = new URI(url);
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setLocation(uri);
                //use TEMPORARY_REDIRECT to forward request from payment gateway to frond-end
                //return new ResponseEntity<>(httpHeaders, HttpStatus.TEMPORARY_REDIRECT);
                return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);
            } catch (Exception ex) {
                LogUtil.error(systemTransactionId, location, "Redirecting error", "", ex);
                response.setSuccessStatus(HttpStatus.OK);
                response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

        }
    }

    @PostMapping(path = {"/callback"}, name = "payments-sp-callback")
    public String spCallback(HttpServletRequest request,
                             @RequestParam(required = false, defaultValue = "") String name,
                             @RequestParam(required = false, defaultValue = "") String email,
                             @RequestParam(required = false, defaultValue = "") String phone,

                             @RequestParam(required = false, defaultValue = "") String amount,
                             @RequestParam(required = false, defaultValue = "") String hash,
                             @RequestParam(required = false, defaultValue = "") int status_id,
                             @RequestParam(required = false, defaultValue = "") String order_id,
                             @RequestParam(required = false, defaultValue = "") String transaction_id,
                             @RequestParam(required = false, defaultValue = "") String msg,
                             @RequestParam(required = false, defaultValue = "") String payment_channel) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "Receive returnUrl ", "Name: " + name + " email: " + email + " phone: " + phone + " amount:" + amount + " hash :" + hash + " orderId: " + order_id + " transactionId: " + transaction_id + " msg: " + msg);
        String systemTransactionId = StringUtility.CreateRefID("CB");
        String IP = request.getRemoteAddr();
        PaymentOrder order = paymentOrdersRepository.findByClientTransactionId(order_id);

        if (order.getStatus().equals("PENDING")) {
            if (status_id == 1) {
                OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_CONFIRMED", "", msg);
                StoreDetails stores = paymentService.getStoreDeliveryDetails(res.getStoreId());
                String spErrorCode = String.valueOf(status_id);
                String statusDescription = msg;
                String paymentTransactionId = transaction_id;
                String clientTransactionId = order_id;
                String status = "PAID";
                PaymentOrder deliveryOrder = paymentOrdersRepository.findByClientTransactionIdAndStatus(order_id, "PENDING");
                if (deliveryOrder != null) {
                    clientTransactionId = deliveryOrder.getClientTransactionId();
                    LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                    deliveryOrder.setStatus(status);
                    deliveryOrder.setPaymentChannel(payment_channel);
                    deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                    deliveryOrder.setSpErrorCode(spErrorCode);
                    deliveryOrder.setSpOrderId(transaction_id);
                    deliveryOrder.setStatusDescription(statusDescription);
                    paymentOrdersRepository.save(deliveryOrder);
                } else {
                    LogUtil.info(systemTransactionId, location, "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");
                }
                response.setSuccessStatus(HttpStatus.OK);
//                response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                return "<html>\n" + "OK" + "\n" + "</html>";
            } else {
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                PaymentOrder deliveryOrder = paymentOrdersRepository.findByClientTransactionIdAndStatus(order_id, "PENDING");
                String spErrorCode = String.valueOf(status_id);
                String statusDescription = msg;
                //TODO : Send Payment Failed Status
                deliveryOrder.setStatus("FAILED");
                deliveryOrder.setPaymentChannel(payment_channel);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(transaction_id);
                deliveryOrder.setStatusDescription(statusDescription);
                paymentOrdersRepository.save(deliveryOrder);
                OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_FAILED", "", msg);

                //fail to get price
                return "<html>\n" + "OK" + "\n" + "</html>";
            }
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");
            return "<html>\n" + "OK" + "\n" + "</html>";

        }

    }

    @PostMapping(path = {"/postTransaction"}, name = "post-sp-transaction", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String postTransaction(HttpServletRequest
                                          request, @RequestBody MultiValueMap<String, String> transaction) throws IOException {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());
        String result = "";

        List<NameValuePair> urlParameters = new ArrayList<>();


        MultiValueMap<String, String> postParameters = new LinkedMultiValueMap<>();
        postParameters.add("CURRENCY_CODE", transaction.get("CURRENCY_CODE").toString());
        postParameters.add("MERCHANT_ID", transaction.get("MERCHANT_ID").toString());
        postParameters.add("MERCHANT_NAME", transaction.get("MERCHANT_NAME").toString());
        postParameters.add("TOKEN", transaction.get("TOKEN").toString());
        postParameters.add("FAILURE_URL", transaction.get("FAILURE_URL").toString());
        postParameters.add("SUCCESS_URL", transaction.get("SUCCESS_URL").toString());
        postParameters.add("CHECKOUT_URL", transaction.get("CHECKOUT_URL").toString());
        postParameters.add("CUSTOMER_EMAIL_ADDRESS", transaction.get("CUSTOMER_EMAIL_ADDRESS").toString());
        postParameters.add("CUSTOMER_MOBILE_NO", transaction.get("CUSTOMER_MOBILE_NO").toString());
        postParameters.add("TXNAMT", transaction.get("TXNAMT").toString());
        postParameters.add("BASKET_ID", transaction.get("BASKET_ID").toString());
        postParameters.add("ORDER_DATE", transaction.get("ORDER_DATE").toString());
        postParameters.add("SIGNATURE", transaction.get("SIGNATURE").toString());
        postParameters.add("VERSION", transaction.get("VERSION").toString());
        postParameters.add("TXNDESC", transaction.get("TXNDESC").toString());
        postParameters.add("PROCCODE", transaction.get("PROCCODE").toString());
        postParameters.add("TRAN_TYPE", transaction.get("TRAN_TYPE").toString());
        postParameters.add("STORE_ID", transaction.get("STORE_ID").toString());

        String systemTransactionId = transaction.get("BASKET_ID").toString();

        System.err.println("POST : " + postParameters);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        headers.add("Host", "ipguat.apps.net.pk");
        headers.add("cookie", "uat_payfast=5ryzfauvxocxic1jb1utmb23");
        headers.add("origin", "https://awan-tech.dev-pk.symplified.ai");
        headers.add("referer", "https://awan-tech.dev-pk.symplified.ai/");
        headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36");
        headers.add("Accept", "*/*");
        headers.add("Cache-Control", "no-cache");
        headers.add("Postman-Token", "52d84ee7-0c49-4854-9224-501f24379d67");
        headers.add("Host", "ipguat.apps.net.pk");
        headers.add("Accept-Encoding", "gzip, deflate, br");
        headers.add("Connection", "keep-alive");
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        headers.add("Content-Length", "1375");
        headers.add("Cookie", "uat_payfast=mzsnfd32i3rrcfw55tqzzlqd");

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
            String targetUrl = "https://ipguat.apps.net.pk/Ecommerce/api/Transaction/PostTransaction";

            LogUtil.info(systemTransactionId, location, "Sending Request to :" + targetUrl, "");
            URL url = new URL(targetUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
//            HttpURLConnection con = (HttpURLConnection) url.openConnection();
//            con.setSSLSocketFactory(sc.getSocketFactory());
//            con.setHostnameVerifier(hv);
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestMethod(String.valueOf(HttpMethod.POST));
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Host", "ipguat.apps.net.pk");
            con.setRequestProperty("origin", "https://awan-tech.dev-pk.symplified.ai");

            con.setRequestProperty("cookie", "uat_payfast=5ryzfauvxocxic1jb1utmb23");
            con.setRequestProperty("referer", "https://awan-tech.dev-pk.symplified.ai/");
            con.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36");
            con.setRequestProperty("Accept", "*/*");
            con.setRequestProperty("Cache-Control", "no-cache");
            con.setRequestProperty("Postman-Token", "52d84ee7-0c49-4854-9224-501f24379d67");
            con.setRequestProperty("Host", "ipguat.apps.net.pk");
            con.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
            con.setRequestProperty("Connection", "keep-alive");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Content-Length", "1375");
            con.setRequestProperty("Cookie", "uat_payfast=mzsnfd32i3rrcfw55tqzzlqd");
            con.connect();

//            if (requestBody != null) {
//                //for post paramters in JSON Format
            OutputStream os = con.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
//
//                LogUtil.info(refId, loglocation, "Request JSON :" + requestBody, "");
            osw.write(postParameters.toString());
            osw.flush();
            osw.close();
//            }

            int responseCode = con.getResponseCode();
            LogUtil.info(systemTransactionId, location, "HTTP Response code:" + responseCode, "");


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

//            Map<String, List<String>> map = con.getHeaderFields();
//            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
//                System.out.println("Key : " + entry.getKey()
//                        + " ,Value : " + entry.getValue());
//                if (entry.getKey().equals("location")) {
//                    LogUtil.info(systemTransactionId, location, "Response of Redirect : " + entry.getValue(), "");
//                }
//            }
            LogUtil.info(systemTransactionId, location, "Response of :" + httpMsgResp.toString(), "");

        } catch (SocketTimeoutException ex) {
            if (ex.getMessage().equals("Read timed out")) {

                LogUtil.error(systemTransactionId, location, "Exception : " + ex.getMessage(), "", ex);
            } else {

                LogUtil.error(systemTransactionId, location, "Exception : " + ex.getMessage(), "", ex);
            }
        } catch (Exception ex) {
            //exception occur

            LogUtil.error(systemTransactionId, location, "Exception during send request : ", "", ex);
        }

        return response.toString();


    }

    @PostMapping(value = {"/PostTransaction"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> mirrorRest(HttpMethod method, HttpServletRequest request, @RequestBody MultiValueMap<String, String> transaction) throws URISyntaxException {

        String targetUrl = "https://ipguat.apps.net.pk/Ecommerce/api/Transaction/PostTransaction";

        MultiValueMap<String, String> postParameters = new LinkedMultiValueMap<>();
        postParameters.add("CURRENCY_CODE", transaction.get("CURRENCY_CODE").toString());
        postParameters.add("MERCHANT_ID", transaction.get("MERCHANT_ID").toString());
        postParameters.add("MERCHANT_NAME", transaction.get("MERCHANT_NAME").toString());
        postParameters.add("TOKEN", transaction.get("TOKEN").toString());
        postParameters.add("FAILURE_URL", transaction.get("FAILURE_URL").toString());
        postParameters.add("SUCCESS_URL", transaction.get("SUCCESS_URL").toString());
        postParameters.add("CHECKOUT_URL", transaction.get("CHECKOUT_URL").toString());
        postParameters.add("CUSTOMER_EMAIL_ADDRESS", transaction.get("CUSTOMER_EMAIL_ADDRESS").toString());
        postParameters.add("CUSTOMER_MOBILE_NO", transaction.get("CUSTOMER_MOBILE_NO").toString());
        postParameters.add("TXNAMT", transaction.get("TXNAMT").toString());
        postParameters.add("BASKET_ID", transaction.get("BASKET_ID").toString());
        postParameters.add("ORDER_DATE", transaction.get("ORDER_DATE").toString());
        postParameters.add("SIGNATURE", transaction.get("SIGNATURE").toString());
        postParameters.add("VERSION", transaction.get("VERSION").toString());
        postParameters.add("TXNDESC", transaction.get("TXNDESC").toString());
        postParameters.add("PROCCODE", transaction.get("PROCCODE").toString());
        postParameters.add("TRAN_TYPE", transaction.get("TRAN_TYPE").toString());
        postParameters.add("STORE_ID", transaction.get("STORE_ID").toString());
        LogUtil.info("logprefix", "location", "Query Proxy  : " + postParameters, "");
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        HttpEntity<MultiValueMap<String, String>> body = new HttpEntity<>(transaction, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> responseEntity = restTemplate.exchange(targetUrl, HttpMethod.POST, body, String.class);
        LogUtil.info(transaction.get("BASKET_ID").toString(), "location", "Response Proxy  : ", responseEntity.getHeaders().toString());
        LogUtil.info(transaction.get("BASKET_ID").toString(), "location", "Response Body Proxy  : ", responseEntity.getBody().toString());

        return responseEntity;
    }

    public String getMd5(String data) {
        try {


            MessageDigest md = MessageDigest.getInstance("MD5");
            // digest() method is called to calculate message digest
            //  of an input digest() return array of byte
            byte[] messageDigest = md.digest(data.getBytes());

            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;

        } catch (Exception exception) {
            return null;

        }
    }
}
