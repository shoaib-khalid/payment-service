package com.kalsym.paymentservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.HttpResponse;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.models.daos.Provider;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.QueryPaymentResult;
import com.kalsym.paymentservice.provider.SpCallbackResult;
import com.kalsym.paymentservice.repositories.*;
import com.kalsym.paymentservice.service.OrderPaymentService;
import com.kalsym.paymentservice.service.Response.OrderConfirm;
import com.kalsym.paymentservice.service.Response.OrderGroup;
import com.kalsym.paymentservice.service.Response.StoreDetails;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import com.kalsym.paymentservice.utils.StringUtility;
import jdk.internal.cmm.SystemResourcePressureImpl;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;


import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigInteger;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;

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

    @Value("${goPayFastPaymentUrl}")
    String goPayFastPaymentUrl;

    @Value("${origin}")
    String origin;

    @PostMapping(path = {"/makePayment"}, name = "payments-make-payment")
    public ResponseEntity<HttpResponse> makePayment(HttpServletRequest request, @Valid @RequestBody PaymentRequest paymentRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "", "");
        paymentRequest.setPaymentAmount(null);
        if (paymentRequest.getChannel() == null)
            paymentRequest.setChannel("DELIVERIN");

        //generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("PY");
        paymentRequest.setSystemTransactionId(systemTransactionId);
        if (paymentRequest.getTransactionId().startsWith("G")) {
            OrderGroup res = paymentService.getGroupOrder(paymentRequest.getTransactionId());

            paymentRequest.setRegionCountryId(res.getRegionCountryId());
            paymentRequest.setPaymentAmount(res.getTotal());

        } else {
            OrderConfirm res = paymentService.getOrderById(paymentRequest.getTransactionId());
            LogUtil.info(systemTransactionId, location, "Order Service Return :   ", res.toString());
            StoreDetails storeDetails = paymentService.getStore(res.getStoreId());

            paymentRequest.setRegionCountryId(storeDetails.getRegionCountryId());
            paymentRequest.setPaymentAmount(res.getOrderGroupDetails().getTotal());
            LogUtil.info(systemTransactionId, location, "Payment Amount  ", paymentRequest.getPaymentAmount().toString());


        }

        Optional<Provider> provider = providerRepository.findByRegionCountryIdAndChannel(paymentRequest.getRegionCountryId(), paymentRequest.getChannel());


        LogUtil.info(systemTransactionId, location, "PaymentOrders Id ", paymentRequest.getTransactionId());

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
    public ResponseEntity<HttpResponse> call(HttpServletRequest request, @RequestParam(name = "name", required = false, defaultValue = "") String name, @RequestParam(name = "email", required = false, defaultValue = "") String email, @RequestParam(name = "phone", required = false, defaultValue = "") String phone, @RequestParam(name = "transaction_amount", required = false, defaultValue = "") String transaction_amount, @RequestParam(name = "status_id", required = false, defaultValue = "") int status_id, @RequestParam(name = "order_id", required = false, defaultValue = "") String order_id, @RequestParam(name = "transaction_id", required = false, defaultValue = "") String transaction_id, @RequestParam(name = "hash", required = false, defaultValue = "") String hash, @RequestParam(name = "basket_id", required = false, defaultValue = "") String basket_id, @RequestParam(name = "Rdv_Message_Key", required = false, defaultValue = "") String Rdv_Message_Key, @RequestParam(name = "PaymentType", required = false, defaultValue = "") String PaymentType, @RequestParam(name = "PaymentName", required = false, defaultValue = "") String PaymentName, @RequestParam(name = "validation_hash", defaultValue = "") String validation_hash, @RequestParam(name = "err_code", required = false) String err_code, @RequestParam(name = "order_date", required = false, defaultValue = "") String order_date, @RequestParam(name = "payment_channel", required = false, defaultValue = "") String payment_channel, @RequestParam(name = "err_msg", required = false, defaultValue = "") String err_msg, @RequestParam(name = "msg", required = false, defaultValue = "") String msg) {

        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());
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
    public String spCallback(HttpServletRequest request, @RequestParam(required = false, defaultValue = "") String name, @RequestParam(required = false, defaultValue = "") String email, @RequestParam(required = false, defaultValue = "") String phone,

                             @RequestParam(required = false, defaultValue = "") String amount, @RequestParam(required = false, defaultValue = "") String hash, @RequestParam(required = false, defaultValue = "") int status_id, @RequestParam(required = false, defaultValue = "") String order_id, @RequestParam(required = false, defaultValue = "") String transaction_id, @RequestParam(required = false, defaultValue = "") String msg, @RequestParam(required = false, defaultValue = "") String payment_channel) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "Receive returnUrl ", "Name: " + name + " email: " + email + " phone: " + phone + " amount:" + amount + " hash :" + hash + " orderId: " + order_id + " transactionId: " + transaction_id + " msg: " + msg);
        String systemTransactionId = StringUtility.CreateRefID("CB");
        String IP = request.getRemoteAddr();
        PaymentOrder order = paymentOrdersRepository.findByClientTransactionId(order_id);

        if (order.getStatus().equals("PENDING")) {
            if (status_id == 1) {
                if (order_id.startsWith("G")) {
                    OrderConfirm res = paymentService.groupOrderUpdateStatus(order_id, "PAYMENT_CONFIRMED", "", msg);
                } else {
                    OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_CONFIRMED", "", msg);
                }
                String spErrorCode = String.valueOf(status_id);
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
                    deliveryOrder.setStatusDescription(msg);
                    paymentOrdersRepository.save(deliveryOrder);
                } else {
                    LogUtil.info(systemTransactionId, location, "DeliveryOrder not found for paymentTransactionId:" + transaction_id, "");
                }
                response.setSuccessStatus(HttpStatus.OK);
//                response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                return "<html>\n" + "OK" + "\n" + "</html>";
            } else {
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                PaymentOrder deliveryOrder = paymentOrdersRepository.findByClientTransactionIdAndStatus(order_id, "PENDING");
                String spErrorCode = String.valueOf(status_id);
                //TODO : Send Payment Failed Status
                deliveryOrder.setStatus("FAILED");
                deliveryOrder.setPaymentChannel(payment_channel);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(transaction_id);
                deliveryOrder.setStatusDescription(msg);
                paymentOrdersRepository.save(deliveryOrder);

                if (order_id.startsWith("G")) {
                    OrderConfirm res = paymentService.groupOrderUpdateStatus(order_id, "PAYMENT_FAILED", "", msg);
                } else {
                    OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_FAILED", "", msg);
                }
                //fail to get price
                return "<html>\n" + "OK" + "\n" + "</html>";
            }
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");
            return "<html>\n" + "OK" + "\n" + "</html>";

        }

    }


    @GetMapping(path = {"/queryOrderStatus/{orderId}"}, name = "payments-sp-query-status")
    public ResponseEntity<HttpResponse> queryOrderStatus(@PathVariable("orderId") String orderId) {
//        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse();

        String systemTransactionId = StringUtility.CreateRefID("CB");
//        String IP = request.getRemoteAddr();
        PaymentOrder order = paymentOrdersRepository.findByClientTransactionId(orderId);

        if (!order.getStatus().equals("PAID")) {

            ProcessRequest process = new ProcessRequest(systemTransactionId, order, providerRatePlanRepository, providerConfigurationRepository, providerRepository);
            ProcessResult processResult = process.QueryPaymentStatus();

            QueryPaymentResult res = (QueryPaymentResult) processResult.returnObject;

            order.setPaymentChannel(res.orderFound.getPaymentChannel());
            order.setStatus(res.orderFound.getStatus());
            order.setSpOrderId(res.orderFound.getSpOrderId());
            paymentOrdersRepository.save(order);
            LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode, "");
            response.setSuccessStatus(HttpStatus.OK);
            if (order.getStatus().equals("PAID")) {
                if (orderId.startsWith("G")) {
                    OrderConfirm updateOrderPaymentStatus = paymentService.groupOrderUpdateStatus(orderId, "PAYMENT_CONFIRMED", "", order.getStatusDescription());
                } else {
                    OrderConfirm updateOrderPaymentStatus = paymentService.updateStatus(orderId, "PAYMENT_CONFIRMED", "", order.getStatusDescription());
                }
            }
            return ResponseEntity.status(response.getStatus()).body(response);
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");
            return ResponseEntity.status(response.getStatus()).body(response);
        }

    }


    @PostMapping(path = {"/payhub2u/callback"}, name = "payments-sp-callback")
    public ResponseEntity<HttpResponse> payhub2uCallback(HttpServletRequest request, @RequestBody Optional<CallbackResponse> payment) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        String systemTransactionId = StringUtility.CreateRefID("CB");
        LogUtil.info(systemTransactionId, location, "Request Callback Body ", payment.toString());

        String host = request.getRemoteHost();
        if (!payment.isPresent()) {
            response.setSuccessStatus(HttpStatus.NOT_FOUND);
            LogUtil.info(systemTransactionId, location, "Callback Body Is Empty ", "");
            return ResponseEntity.status(response.getStatus()).body(response);
        }
        System.err.println("TRB" + payment.get().getTransactionId());
        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(payment.get().getTransactionId());

        if (order.getStatus().equals("PENDING")) {
            if (payment.get().getStatus().equals("paid")) {
                if (payment.get().getAmount().equals(order.getPaymentAmount())) {
                    if (order.getClientTransactionId().startsWith("G")) {
                        OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(), "PAYMENT_CONFIRMED", "", payment.get().getStatus());
                    } else {
                        OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_CONFIRMED", "", payment.get().getStatus());
                    }
                    String spErrorCode = payment.get().getStatus();
                    String statusDescription = payment.get().getStatus();
                    String paymentTransactionId = payment.get().getId();
                    String clientTransactionId = payment.get().getTransactionId();
                    String status = "PAID";
                    PaymentOrder deliveryOrder = paymentOrdersRepository.findBySystemTransactionIdAndStatus(payment.get().getTransactionId(), "PENDING");
                    if (deliveryOrder != null) {
                        clientTransactionId = deliveryOrder.getClientTransactionId();
                        LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                        deliveryOrder.setStatus(status);
                        deliveryOrder.setPaymentChannel(payment.get().getBank());
                        deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                        deliveryOrder.setSpErrorCode(spErrorCode);
                        deliveryOrder.setSpOrderId(payment.get().getId());
                        deliveryOrder.setStatusDescription(statusDescription);
                        paymentOrdersRepository.save(deliveryOrder);
                    } else {
                        LogUtil.info(systemTransactionId, location, "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");
                    }
                    response.setSuccessStatus(HttpStatus.OK);
//                response.setData(processResult.returnObject);
                    LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                    response.setSuccessStatus(HttpStatus.OK);
                    return ResponseEntity.status(response.getStatus()).body(response);
                } else {
                    if (order.getClientTransactionId().startsWith("G")) {
                        OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "", "Transaction Amount Does Not Match");
                    } else {
                        OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "", "Transaction Amount Does Not Match");
                    }
                    response.setSuccessStatus(HttpStatus.OK);
                    return ResponseEntity.status(response.getStatus()).body(response);
                }
            }else if(payment.get().getStatus().equals("pending")) {
                response.setSuccessStatus(HttpStatus.OK);
                return ResponseEntity.status(response.getStatus()).body(response);
            }

            else {
                if (order.getClientTransactionId().startsWith("G")) {
                    OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "", payment.get().getStatus());
                } else {
                    OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "", payment.get().getStatus());
                }
            }
            response.setSuccessStatus(HttpStatus.OK);
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");

        }
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @Getter
    @Setter
    public static class CallbackResponse {

        @JsonProperty("id")
        String id;
        @JsonProperty("transactionId")
        String transactionId;
        @JsonProperty("status")
        String status;
        @JsonProperty("provider")
        String provider;
        @JsonProperty("bank")
        String bank;
        @JsonProperty("bankName")
        String bankName;
        @JsonProperty("chargeAmount")
        Double chargeAmount;
        @JsonProperty("totalAmount")
        Double totalAmount;
        @JsonProperty("amount")
        Double amount;

        public String toString() {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(this);
        }

    }

}
