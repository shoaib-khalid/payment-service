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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;


import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.websocket.server.PathParam;
import java.math.BigInteger;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HashMap;
import java.io.*;
import java.util.Map;

import okhttp3.*;

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


/*    @RequestMapping(method = RequestMethod.GET, value = "/querypayment/{payment-id}", name = "payments-query-payment")
    public ResponseEntity<HttpReponse> queryPayment(HttpServletRequest request,
            @PathVariable("payment-id") String orderId) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "", "");

        //generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("DL");
        Optional<PaymentOrder> orderDetails = paymentOrdersRepository.findById(orderId);
        if (orderDetails.isPresent()) {
            ProcessRequest process = new ProcessRequest(systemTransactionId, orderDetails.get(), providerRatePlanRepository, providerConfigurationRepository, providerRepository);
            ProcessResult processResult = process.QueryPayment();
            LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:"+processResult.resultCode, "");

            if (processResult.resultCode==0) {
                //successfully get price from provider
                response.setSuccessStatus(HttpStatus.OK);
                response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with "+HttpStatus.OK, "");
                return ResponseEntity.status(HttpStatus.OK).body(response);
            } else {
                //fail to get price
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } else {
            LogUtil.info(systemTransactionId, location, "DeliveyOrder not found for orderId:"+orderId, "");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }*/

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
        } else {
            requestBody.addProperty("err_msg", err_msg);
            requestBody.addProperty("err_code", err_code);
            requestBody.addProperty("order_date", order_date);
        }

        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(basket_id);
        LogUtil.info(logprefix, location, "IP:" + IP, order.getClientTransactionId());
        LogUtil.info(logprefix, location, "IP:" + IP, order.getClientTransactionId());
        LogUtil.info(logprefix, location, "IP:" + IP, providerIpRepository.toString());
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
    public Response postTransaction(HttpServletRequest
                                            request, @RequestBody MultiValueMap<String, String> transaction) throws IOException {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());
        String result = "";

        MultiValueMap<String, Object> postParameters = new LinkedMultiValueMap<>();
        postParameters.add("CURRENCY_CODE", transaction.get("CURRENCY_CODE"));
        postParameters.add("MERCHANT_ID", transaction.get("MERCHANT_ID"));
        postParameters.add("MERCHANT_NAME", transaction.get("MERCHANT_NAME"));
        postParameters.add("TOKEN", transaction.get("TOKEN"));
        postParameters.add("FAILURE_URL", transaction.get("FAILURE_URL"));
        postParameters.add("SUCCESS_URL", transaction.get("SUCCESS_URL"));
        postParameters.add("CHECKOUT_URL", transaction.get("CHECKOUT_URL"));
        postParameters.add("CUSTOMER_EMAIL_ADDRESS", transaction.get("CUSTOMER_EMAIL_ADDRESS"));
        postParameters.add("CUSTOMER_MOBILE_NO", transaction.get("CUSTOMER_MOBILE_NO"));
        postParameters.add("TXNAMT", transaction.get("TXNAMT"));
        postParameters.add("BASKET_ID", transaction.get("BASKET_ID"));
        postParameters.add("ORDER_DATE", transaction.get("ORDER_DATE"));
        postParameters.add("SIGNATURE", transaction.get("SIGNATURE"));
        postParameters.add("VERSION", transaction.get("VERSION"));
        postParameters.add("TXNDESC", transaction.get("TXNDESC"));
        postParameters.add("PROCCODE", transaction.get("PROCCODE"));
        postParameters.add("TRAN_TYPE", transaction.get("TRAN_TYPE"));
        postParameters.add("STORE_ID", transaction.get("STORE_ID"));

        System.err.println("POST : " + postParameters);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        headers.add("Cookie", "production_payfast=wz3xqywlvj5pvuxjycaasp5q");
        headers.add("Host", "ipguat.apps.net.pk");


        HttpEntity<MultiValueMap<String, Object>> res = new HttpEntity<>(postParameters, headers);
        String getPaymentLnk = "https://ipguat.apps.net.pk/Ecommerce/api/Transaction/PostTransaction";

        HashMap httpHeader = new HashMap();
        httpHeader.put("Content-Type", "application/x-www-form-urlencoded");

        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/x-www-form-urlencoded");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, "CURRENCY_CODE=PKR&MERCHANT_ID=13464&MERCHANT_NAME=EasyDukan%20Pvt%20Ltd&TOKEN=xIWTFj_5h9swoeopbKHlylWtSqukq6o4rePqQDw0mB0&SUCCESS_URL=https://dev-pk.symplified.ai/payment-redirect?name=Irsakumar%26email=irasakumar41@gmail.com%26phone=920192802728%26amount=227.88%26hash=%26status_id=1%26order_id=test123%26transaction_id=20220602052018%26msg=Payment_was_successful%26payment_channel=fastpay&FAILURE_URL=https://dev-pk.symplified.ai/payment-redirect?name=Irsakumar%26email=irasakumar41@gmail.com%26phone=920192802728%26amount=227.88%26hash=%26status_id=0%26order_id=test123%26transaction_id=20220602052018%26msg=Payment_was_failed%26payment_channel=fastpay&CHECKOUT_URL=awan-tech.dev-pk.symplified.ai/checkout&CUSTOMER_EMAIL_ADDRESS=irasakumar41@gmail.com&CUSTOMER_MOBILE_NO=920192802728&TXNAMT=762.6&BASKET_ID=PY030622094111dbb0&ORDER_DATE=2022-06-03%2005:20:18&SIGNATURE=SOME-RANDOM-STRING&VERSION=MERCHANT-CART-0.1&TXNDESC=Item%20purchased%20from%20EasyDukan&PROCCODE=00&TRAN_TYPE=ECOMM_PURCHASE&STORE_ID=");

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Request r = new Request.Builder()
                .url(getPaymentLnk)
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        Response results = client.newCall(r).execute();
        System.err.println("Response" + results.body().string());
        return results;

//        try {
//            RestTemplate restTemplate = new RestTemplate();
//            ResponseEntity<String> responses = restTemplate.exchange(getPaymentLnk, HttpMethod.POST, res, String.class);
////            HttpResult httpResult = HttpsPostConn.SendHttpsRequest("POST", String.valueOf(transaction.get("BASKET_ID")), getPaymentLnk, httpHeader,postParameters.toString(), 10000, 1500);
//
//            int statusCode = responses.getStatusCode().value();
//            LogUtil.info(logprefix, location, "Responses", responses.getBody());
//            if (statusCode == 200) {
//                LogUtil.info(logprefix, location, "Get Token: " + responses.getBody(), "");
//
////                JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
////                token = jsonResp.get("token").getAsString();
//                System.err.println("Header" + responses.getHeaders());
//                return responses.getBody();
//
//            } else {
//
//                LogUtil.info(logprefix, location, "Request failed", responses.getBody());
//                result = "";
//                System.err.println("Header" + responses.getHeaders());
//
//                return responses.getBody();
//
//            }
//            if (httpResult.resultCode==0) {
//                LogUtil.info(logprefix, location, "Request successful", httpResult.responseString);
//
//            } else {
//                LogUtil.info(logprefix, location, "Request failed", "");
////            }
//    } catch(
//    Exception exception)
//
//    {
//        LogUtil.info(logprefix, location, "Exception : ", exception.getMessage());
//        result = exception.getMessage();
//    }


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
