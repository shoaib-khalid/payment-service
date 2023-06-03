package com.kalsym.paymentservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.HttpResponse;
import com.kalsym.paymentservice.models.daos.*;
import com.kalsym.paymentservice.models.dto.CreditCartPaymentOption;
import com.kalsym.paymentservice.models.dto.PaymentRequest;
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
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import springfox.documentation.spring.web.json.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    @Autowired
    CustomersRepository customersRepository;

    @Value("${paymentRedirectUrl}")
    String paymentRedirectUrl;

    @Value("${goPayFastPaymentUrl}")
    String goPayFastPaymentUrl;

    @Value("${origin}")
    String origin;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    OrderRepository orderRepository;

//    @Autowired
//    StorePaymentDetailsRepository storePaymentDetailsRepository;

    @PostMapping(path = {"/makePayment"}, name = "payments-make-payment")
    public ResponseEntity<HttpResponse> makePayment(HttpServletRequest request,
                                                    @Valid @RequestBody PaymentRequest paymentRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        // Customer customer =
        // customersRepository.getOne(paymentRequest.getCustomerId());

        LogUtil.info(logprefix, location, "", "");
        paymentRequest.setPaymentAmount(null);
        if (paymentRequest.getChannel() == null)
            paymentRequest.setChannel("DELIVERIN");

        // generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("PY");
        paymentRequest.setSystemTransactionId(systemTransactionId);
        if (paymentRequest.getTransactionId().startsWith("G")) {
            OrderGroup res = paymentService.getGroupOrder(paymentRequest.getTransactionId());

            paymentRequest.setRegionCountryId(res.getRegionCountryId());
            paymentRequest.setPaymentAmount(res.getTotal());
            paymentRequest.setStoreVerticalCode("");

        } else {
            OrderConfirm res = paymentService.getOrderById(paymentRequest.getTransactionId());
            LogUtil.info(systemTransactionId, location, "Order Service Return :   ", res.toString());
            StoreDetails storeDetails = paymentService.getStore(res.getStoreId());

            paymentRequest.setRegionCountryId(storeDetails.getRegionCountryId());
            paymentRequest.setStoreVerticalCode(storeDetails.getVerticalCode());
            paymentRequest.setPaymentAmount(res.getOrderGroupDetails().getTotal());
            LogUtil.info(systemTransactionId, location, "Payment Amount  ",
                    paymentRequest.getPaymentAmount().toString());
        }
        paymentRequest.setOnlinePayment(true);
        paymentRequest.setBrowser("WEBSITE");
        paymentRequest.setOrderInvoiceNo(generateUniqueString());
        paymentRequest.setBrowser("WEBSITE");

        // Optional<Provider> provider =
        // providerRepository.findByRegionCountryIdAndChannel(paymentRequest.getRegionCountryId(),
        // paymentRequest.getChannel());

        LogUtil.info(systemTransactionId, location, "PaymentOrders Id ", paymentRequest.getTransactionId());

        ProcessRequest process = new ProcessRequest(systemTransactionId, paymentRequest, providerRatePlanRepository,
                providerConfigurationRepository, providerRepository);
        ProcessResult processResult = process.MakePayment();
        LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode
                + " isSuccess:" + processResult.isSuccess, "");

        if (processResult.isSuccess) {
            // successfully submit order to provider
            // store result in delivery order
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
            paymentOrder.setUniquePaymentId(paymentRequest.getOrderInvoiceNo());
            paymentOrdersRepository.save(paymentOrder);

            response.setSuccessStatus(HttpStatus.OK);
            response.setData(processResult.returnObject);
            LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } else {
            // fail to make payment
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // TODO : Proper way callback in testing
    // FIXME: Check Where This API Using
    @GetMapping(path = {"/payment-redirect"}, name = "payments-sp-callback")
    public ResponseEntity<HttpResponse> call(HttpServletRequest request,
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
        HttpResponse response = new HttpResponse(request.getRequestURI());
        System.err.println("NAME " + validation_hash);

        LogUtil.info(logprefix, location, "receive callback from Provider", "");
        // generate transaction id
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
        ProcessRequest process = new ProcessRequest(systemTransactionId, requestBody, providerRatePlanRepository,
                providerConfigurationRepository, providerRepository);
        ProcessResult processResult = process.ProcessCallback(IP, providerIpRepository, order.getSpId());
        LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode,
                "");

        if (processResult.resultCode == 0) {
            try {

                OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_CONFIRMED", "", msg);
            } catch (Exception ex) {
                LogUtil.info(systemTransactionId, location, "Payment Update Failed: ", ex.getMessage());

            }
            // update order status in db
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
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime",
                        "");
                deliveryOrder.setStatus(status);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(spOrderId);
                paymentOrdersRepository.save(deliveryOrder);
            } else {
                LogUtil.info(systemTransactionId, location,
                        "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");

            }
            try {
                // send redirect to Thank You page
                String url = paymentRedirectUrl + "name=" + name + "&email=" + email + "&phone=" + phone + "&amount="
                        + transaction_amount + "&hash=&status_id=" + statusId + "&order_id=" + spOrderId
                        + "&transaction_id=" + paymentTransactionId + "&msg=" + status + "&payment_channel="
                        + paymentChanel;
                LogUtil.info(systemTransactionId, location, "Redirect to url:" + url + " with " + HttpStatus.SEE_OTHER,
                        "");
                URI uri = new URI(url);
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setLocation(uri);
                // use TEMPORARY_REDIRECT to forward request from payment gateway to frond-end
                // return new ResponseEntity<>(httpHeaders, HttpStatus.TEMPORARY_REDIRECT);
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
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime",
                        "");
                deliveryOrder.setStatus(status);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(spOrderId);
                paymentOrdersRepository.save(deliveryOrder);
            } else {
                LogUtil.info(systemTransactionId, location,
                        "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");

            }
            try {
                // send redirect to Thank You page
                String url = paymentRedirectUrl + "name=" + name + "&email=" + email + "&phone=" + phone + "&amount="
                        + transaction_amount + "&hash=&status_id=" + statusId + "&order_id=" + spOrderId
                        + "&transaction_id=" + paymentTransactionId + "&msg=" + status + "&payment_channel="
                        + paymentChanel;
                LogUtil.info(systemTransactionId, location, "Redirect to url:" + url + " with " + HttpStatus.SEE_OTHER,
                        "");
                URI uri = new URI(url);
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setLocation(uri);
                // use TEMPORARY_REDIRECT to forward request from payment gateway to frond-end
                // return new ResponseEntity<>(httpHeaders, HttpStatus.TEMPORARY_REDIRECT);
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

    @GetMapping(path = {"/queryOrderStatus/{orderId}"}, name = "payments-sp-query-status")
    public ResponseEntity<HttpResponse> queryOrderStatus(@PathVariable("orderId") String orderId) {
        // String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse();

        String systemTransactionId = StringUtility.CreateRefID("CB");
        // String IP = request.getRemoteAddr();
        PaymentOrder order = paymentOrdersRepository.findByClientTransactionId(orderId).get();

        if (!order.getStatus().equals("PAID")) {

            ProcessRequest process = new ProcessRequest(systemTransactionId, order, providerRatePlanRepository,
                    providerConfigurationRepository, providerRepository);
            ProcessResult processResult = process.QueryPaymentStatus();

            QueryPaymentResult res = (QueryPaymentResult) processResult.returnObject;

            order.setPaymentChannel(res.orderFound.getPaymentChannel());
            order.setStatus(res.orderFound.getStatus());
            order.setSpOrderId(res.orderFound.getSpOrderId());
            paymentOrdersRepository.save(order);
            LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode,
                    "");
            response.setSuccessStatus(HttpStatus.OK);
            if (order.getStatus().equals("PAID")) {
                if (orderId.startsWith("G")) {
                    OrderConfirm updateOrderPaymentStatus = paymentService.groupOrderUpdateStatus(orderId,
                            "PAYMENT_CONFIRMED", "", order.getStatusDescription());
                } else {
                    OrderConfirm updateOrderPaymentStatus = paymentService.updateStatus(orderId, "PAYMENT_CONFIRMED",
                            "", order.getStatusDescription());
                }
            }
            return ResponseEntity.status(response.getStatus()).body(response);
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");
            return ResponseEntity.status(response.getStatus()).body(response);
        }

    }

    // CALLBACK HANDLING
    // SENANGPAY CALLBACK
    @PostMapping(path = {"/callback"}, name = "payments-sp-callback")
    public String spCallback(HttpServletRequest request, @RequestParam(required = false, defaultValue = "") String name,
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
        HttpResponse response = new HttpResponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "Receive returnUrl ",
                "Name: " + name + " email: " + email + " phone: " + phone + " amount:" + amount + " hash :" + hash
                        + " orderId: " + order_id + " transactionId: " + transaction_id + " msg: " + msg);
        String systemTransactionId = StringUtility.CreateRefID("CB");
        String IP = request.getRemoteAddr();
        PaymentOrder order = paymentOrdersRepository.findByClientTransactionId(order_id).get();

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
                PaymentOrder deliveryOrder = paymentOrdersRepository.findByClientTransactionIdAndStatus(order_id,
                        "PENDING");
                if (deliveryOrder != null) {
                    clientTransactionId = deliveryOrder.getClientTransactionId();
                    LogUtil.info(systemTransactionId, location,
                            "DeliveryOrder found. Update status and updated datetime", "");
                    deliveryOrder.setStatus(status);
                    deliveryOrder.setPaymentChannel(payment_channel);
                    deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                    deliveryOrder.setSpErrorCode(spErrorCode);
                    deliveryOrder.setSpOrderId(transaction_id);
                    deliveryOrder.setStatusDescription(msg);
                    paymentOrdersRepository.save(deliveryOrder);
                } else {
                    LogUtil.info(systemTransactionId, location,
                            "DeliveryOrder not found for paymentTransactionId:" + transaction_id, "");
                }
                response.setSuccessStatus(HttpStatus.OK);
                // response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                return "<html>\n" + "OK" + "\n" + "</html>";
            } else {
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime",
                        "");
                PaymentOrder deliveryOrder = paymentOrdersRepository.findByClientTransactionIdAndStatus(order_id,
                        "PENDING");
                String spErrorCode = String.valueOf(status_id);
                // TODO : Send Payment Failed Status
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
                // fail to get price
                return "<html>\n" + "OK" + "\n" + "</html>";
            }
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");
            return "<html>\n" + "OK" + "\n" + "</html>";

        }

    }

    @PostMapping(path = {"/payhub2u/callback"}, name = "payments-sp-callback")
    public ResponseEntity<HttpResponse> payhub2uCallback(HttpServletRequest request,
                                                         @RequestBody Optional<CallbackResponse> payment) {
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
                        OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(),
                                "PAYMENT_CONFIRMED", "", payment.get().getStatus());
                    } else {
                        OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(),
                                "PAYMENT_CONFIRMED", "", payment.get().getStatus());
                    }
                    String spErrorCode = payment.get().getStatus();
                    String statusDescription = payment.get().getStatus();
                    String paymentTransactionId = payment.get().getId();
                    String clientTransactionId = payment.get().getTransactionId();
                    String status = "PAID";
                    PaymentOrder deliveryOrder = paymentOrdersRepository
                            .findBySystemTransactionIdAndStatus(payment.get().getTransactionId(), "PENDING");
                    if (deliveryOrder != null) {
                        clientTransactionId = deliveryOrder.getClientTransactionId();
                        LogUtil.info(systemTransactionId, location,
                                "DeliveryOrder found. Update status and updated datetime", "");
                        deliveryOrder.setStatus(status);
                        deliveryOrder.setPaymentChannel(payment.get().getBank());
                        deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                        deliveryOrder.setSpErrorCode(spErrorCode);
                        deliveryOrder.setSpOrderId(payment.get().getId());
                        deliveryOrder.setStatusDescription(statusDescription);
                        paymentOrdersRepository.save(deliveryOrder);
                    } else {
                        LogUtil.info(systemTransactionId, location,
                                "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");
                    }
                    response.setSuccessStatus(HttpStatus.OK);
                    // response.setData(processResult.returnObject);
                    LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                    response.setSuccessStatus(HttpStatus.OK);
                    return ResponseEntity.status(response.getStatus()).body(response);
                } else {
                    if (order.getClientTransactionId().startsWith("G")) {
                        OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(),
                                "PAYMENT_FAILED", "", "Transaction Amount Does Not Match");
                    } else {
                        OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_FAILED",
                                "", "Transaction Amount Does Not Match");
                    }
                    response.setSuccessStatus(HttpStatus.OK);
                    return ResponseEntity.status(response.getStatus()).body(response);
                }
            } else if (payment.get().getStatus().equals("pending")) {
                response.setSuccessStatus(HttpStatus.OK);
                return ResponseEntity.status(response.getStatus()).body(response);
            } else {
                if (order.getClientTransactionId().startsWith("G")) {
                    OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(),
                            "PAYMENT_FAILED", "", payment.get().getStatus());
                } else {
                    OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "",
                            payment.get().getStatus());
                }
            }
            response.setSuccessStatus(HttpStatus.OK);
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");

        }
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    // BetterPay
    // TO Generate Invoice Id
    @PostMapping(path = {"/request/makePayment"}, name = "payments-request-makepayment")
    public ResponseEntity<HttpResponse> requestMakePayment(HttpServletRequest request,
                                                           @Valid @RequestBody RequestPayment paymentRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        // Customer customer =
        // customersRepository.getOne(paymentRequest.getCustomerId());
        Optional<PaymentOrder> orderPresent = paymentOrdersRepository
                .findByClientTransactionId(paymentRequest.getOrderId());
        if (orderPresent.isPresent()) {
            response.setSuccessStatus(HttpStatus.OK);
            response.setData(orderPresent.get());
            LogUtil.info(orderPresent.get().getSystemTransactionId(), location, "Response with " + HttpStatus.OK, "");
        } else {
            LogUtil.info(logprefix, location, "", "");
            paymentRequest.setPaymentAmount(null);
            if (paymentRequest.getChannel() == null)
                paymentRequest.setChannel("DELIVERIN");

            // generate transaction id
            String systemTransactionId = StringUtility.CreateRefID("PY");
            if (paymentRequest.getOrderId().startsWith("G")) {
                OrderGroup res = paymentService.getGroupOrder(paymentRequest.getOrderId());

                paymentRequest.setRegionCountryId(res.getRegionCountryId());
                paymentRequest.setPaymentAmount(res.getTotal());

            } else {
                OrderConfirm res = paymentService.getOrderById(paymentRequest.getOrderId());
                LogUtil.info(systemTransactionId, location, "Order Service Return :   ", res.toString());
                StoreDetails storeDetails = paymentService.getStore(res.getStoreId());

                paymentRequest.setRegionCountryId(storeDetails.getRegionCountryId());
                paymentRequest.setStoreVerticalCode(storeDetails.getVerticalCode());
                paymentRequest.setPaymentAmount(res.getOrderGroupDetails().getTotal());
                LogUtil.info(systemTransactionId, location, "Payment Amount  ",
                        paymentRequest.getPaymentAmount().toString());

            }
            // successfully submit order to provider
            // store result in delivery order
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setClientTransactionId(paymentRequest.getOrderId());
            paymentOrder.setSystemTransactionId(systemTransactionId);
            paymentOrder.setStatus("PENDING");
            paymentOrder.setPaymentChannel(paymentRequest.getPaymentType());
            paymentOrder.setCreatedDate(DateTimeUtil.currentTimestamp());
            paymentOrder.setSpId(5);
            paymentOrder.setProductCode("parcel");
            paymentOrder.setPaymentAmount(paymentRequest.getPaymentAmount());

            LogUtil.info(systemTransactionId, location, "PaymentOrder ", paymentOrder.toString());
            paymentOrdersRepository.save(paymentOrder);
            response.setSuccessStatus(HttpStatus.OK);
            response.setData(paymentOrder);
            LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
        }
        return ResponseEntity.status(HttpStatus.OK).body(response);

    }

    // Get Payment Details
    @GetMapping(path = {"/getPaymentDetails/{invoiceId}"}, name = "payments-get-details")
    public ResponseEntity<HttpResponse> getPaymentInvoiceDetails(HttpServletRequest request,
                                                                 @PathVariable(name = "invoiceId") String invoiceId) throws ParseException {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());
        PaymentDetails paymentDetails = new PaymentDetails();

        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(invoiceId);
        if (order != null) {

            Date current = new Date();

            // Parse the string date into a Date object
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = dateFormat.parse(order.getCreatedDate());
            System.err.println("Current Date" + current.toString());
            System.err.println("Current Date" + date.toString());

            // Find the difference between the current date and the string date in minutes
            long diff = (current.getTime() - date.getTime()) / (1000 * 60);
            System.err.println("ORDER ID  { " + order.getClientTransactionId() + "}");

            Order storeOrder = orderRepository.getOne(order.getClientTransactionId());
            System.err.println("ORDER { " + storeOrder.toString() + "}");

            if (Math.abs(diff) < 3) {
                // The difference is less than 5 minutes
                System.out.println("The difference is less than 5 minutes.");
                paymentDetails.setStoreName(storeOrder.getStore().getName());
                paymentDetails.setStatus("ACTIVE");
                paymentDetails.setOrderTotalAmount(order.getPaymentAmount());
                paymentDetails.setCustomerId(order.getCustomerId());
                paymentDetails.setInvoiceId(order.getSystemTransactionId());
                response.setData(paymentDetails);
                response.setSuccessStatus(HttpStatus.OK);

            } else {
                paymentDetails.setStoreName(storeOrder.getStore().getName());
                paymentDetails.setStatus("EXPIRED");
                paymentDetails.setCustomerId(order.getCustomerId());
                paymentDetails.setInvoiceId(order.getSystemTransactionId());
                // The difference is 5 minutes or more
                System.out.println("The difference is 5 minutes or more.");

                response.setData(paymentDetails);
                response.setSuccessStatus(HttpStatus.FORBIDDEN);
            }
        } else {
            response.setSuccessStatus(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.status(response.getStatus()).body(response);

    }

    @GetMapping(path = {"/get/BNPLList"}, name = "payment-get-bnpl-response")
    public ResponseEntity<HttpResponse> getBNPLList(HttpServletRequest request) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        RestTemplate restTemplate = new RestTemplate();
        // String requestUrl =
        // "https://www.demo.betterpay.me/merchant/api/v2/lite/channels";//Staging
        String requestUrl = "https://lite.betterpay.me/api/merchant/v1/channels";

        // String merchantId = "10363";Staging
        String merchantId = "R1184";// production
        JsonObject object = new JsonObject();
        object.addProperty("merchant_id", merchantId);
        String hmacHex = "";
        String message = merchantId;
        System.err.println(message);
        // String secret = "XPePraM9Lsgz";//Staging
        String secret = "MWsREUapAZ";// Production

        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            hmacSha256.init(secretKeySpec);

            byte[] hmacBytes = hmacSha256.doFinal(message.getBytes());

            // Convert the byte array to a hexadecimal string representation
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            hmacHex = sb.toString();

            System.out.println("HMAC-SHA256: " + hmacHex);
        } catch (Exception e) {
            LogUtil.info(merchantId, location, "Better Pay HMAC Exception  ", e.getMessage());

        }
        object.addProperty("hash", hmacHex);
        System.err.println("request Body " + object.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (!requestUrl.contains("demo")) {
            headers.set("Host", "lite.betterpay.me");
            headers.set("Content-Length", "111");
            headers.set("User-Agent", "PostmanRuntime/7.32.2");
        }
        HttpEntity<String> data = new HttpEntity<>(object.toString(), headers);
        try {
            ResponseEntity<String> responses = restTemplate.exchange(requestUrl, HttpMethod.POST, data, String.class);

            int statusCode = responses.getStatusCode().value();
            LogUtil.info(logprefix, location, "Responses", responses.getBody());
            if (statusCode == 200) {
                LogUtil.info(logprefix, location, "Get Token: " + responses.getBody(), "");

                JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                LogUtil.info(logprefix, location, "Get Response In Json: " + jsonResp.toString(), "");
                List<BNPLLIST> bnpllists = new ArrayList<>();
                for (JsonElement responseData : jsonResp.getAsJsonArray("data")) {
                    BNPLLIST bnpllist = new BNPLLIST();
                    LogUtil.info(logprefix, location, "Get Response In Json: " + responseData, "");
                    JsonObject list = new Gson().fromJson(responseData, JsonObject.class);
                    if (list.get("type").getAsString().equals("BNPL")) {
                        bnpllist.setType(list.get("type").getAsString());
                        bnpllist.setProviderName(list.get("display_name").getAsString());
                        bnpllist.setProviderValue(list.get("value").getAsString());
                        bnpllist.setLogoUrl(list.get("logo_url").getAsString());
                        bnpllists.add(bnpllist);
                    }
                }
                response.setData(bnpllists);
                response.setSuccessStatus(HttpStatus.OK);

            } else {
                JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                // betterPayResponse.setPaymentUrl("");
                // betterPayResponse.setMessage(jsonResp.get("comment").getAsString());
                //
                // response.setData(betterPayResponse);
                response.setSuccessStatus(HttpStatus.BAD_REQUEST);

                LogUtil.info(logprefix, location, "Request failed", responses.getBody());
            }

        } catch (Exception ex) {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info("BNPL-LIST", location, "Exception ", ex.getMessage());
        }
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @PostMapping(path = {"/payment-request"}, name = "payments-make-payment")
    public ResponseEntity<HttpResponse> betterPaymentReqeust(HttpServletRequest request,
                                                             @Valid @RequestBody BetterPayRequest betterPayRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        // Customer customer =
        // customersRepository.getOne(betterPayRequest.getCustomerId());
        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(betterPayRequest.getTransactionId());

        // generate transaction id
        Order storeOrder;

        if (order.getClientTransactionId().startsWith("G")) {
            OrderGroup res = paymentService.getGroupOrder(order.getClientTransactionId());
            betterPayRequest.setOrderTotalAmount(res.getTotal());
            storeOrder = orderRepository.findAllByOrderGroupId(order.getClientTransactionId()).get(0);

        } else {
            OrderConfirm res = paymentService.getOrderById(order.getClientTransactionId());
            LogUtil.info(order.getSystemTransactionId(), location, "Order Service Return :   ", res.toString());
            betterPayRequest.setOrderTotalAmount(res.getTotal());
            StoreDetails storeDetails = paymentService.getStore(res.getStoreId());
            LogUtil.info(order.getSystemTransactionId(), location, "Payment Amount  ", res.getTotal().toString());
            storeOrder = orderRepository.findById(order.getClientTransactionId()).get();

        }

        PaymentRequest paymentRequest = new PaymentRequest();
        CreditCartPaymentOption card = new CreditCartPaymentOption();

        paymentRequest.setBrowser("TERMINAL");
        paymentRequest.setRegionCountryId("MYS");
        paymentRequest.setChannel("DELIVERIN");
        paymentRequest.setPaymentAmount(storeOrder.getTotal().doubleValue());
        paymentRequest.setOrderInvoiceNo(storeOrder.getInvoiceId());
        paymentRequest.setPaymentDescription(order.getSystemTransactionId());
        paymentRequest.setCustomerName(betterPayRequest.getCustomerName());
        paymentRequest.setEmail(betterPayRequest.getEmail());
        paymentRequest.setPhoneNo(betterPayRequest.getPhoneNo());
        paymentRequest.setPaymentType(betterPayRequest.getPaymentType());
        paymentRequest.setPaymentService(betterPayRequest.getPaymentService());
        paymentRequest.setSystemTransactionId(order.getSystemTransactionId());

        card.setCardNo(betterPayRequest.getCreditCardNo());
        card.setCardCCV(betterPayRequest.getCardCCV());
        card.setCardYear(betterPayRequest.getCardYear());
        card.setCardMonth(betterPayRequest.getCardMonth());
        card.setBankCode(betterPayRequest.getPaymentType());
        paymentRequest.setCreditCardPaymentOptions(card);

//        Optional<StorePaymentDetails> storePaymentDetails = storePaymentDetailsRepository.findById(storeOrder.getStore().getId());
//        storePaymentDetails.ifPresent(paymentRequest::setStorePaymentDetailsOptional);


        ProcessRequest process = new ProcessRequest(order.getSystemTransactionId(), paymentRequest,
                providerRatePlanRepository, providerConfigurationRepository, providerRepository);
        ProcessResult processResult = process.MakePayment();
        LogUtil.info(order.getSystemTransactionId(), location, "ProcessRequest finish. resultCode:"
                + processResult.resultCode + " isSuccess:" + processResult.isSuccess, "");

        if (processResult.isSuccess) {

            MakePaymentResult paymentOrderResult = (MakePaymentResult) processResult.returnObject;
            BetterPayResponse betterPayResponse = new BetterPayResponse();
            betterPayResponse.setMessage(paymentOrderResult.description);
            betterPayResponse.setPaymentUrl(paymentOrderResult.paymentLink);
            LogUtil.info(order.getSystemTransactionId(), location, "Response with " + HttpStatus.OK, betterPayResponse.toString());


            response.setSuccessStatus(HttpStatus.OK);
            response.setData(betterPayResponse);
            LogUtil.info(order.getSystemTransactionId(), location, "Response with " + HttpStatus.OK, "");
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } else {
            // fail to make payment
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // move to betterpayment class
    }

    // Callback
    @PostMapping(path = {
            "/request/callback"}, name = "payment-request-callback")
    public ResponseEntity<HttpResponse> paymentRequestCallback(HttpServletRequest request,
                                                               @RequestBody Optional<BetterPayCallbackResponse> requestBody) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        String systemTransactionId = StringUtility.CreateRefID("CB");
        LogUtil.info(systemTransactionId, location, "Request Callback Body ", requestBody.toString());
        LogUtil.info(systemTransactionId, location, "Request Headers Body ", request.getContentType());

        String IP = request.getRemoteAddr();
        LogUtil.info(logprefix, location, "Get Provider List  : ", IP);

        BetterPayCallbackResponse callbackResponse = requestBody.get();
        LogUtil.info(systemTransactionId, location, "Request Callback Body Convert To Json ",
                callbackResponse.toString());

        try {
            String paymentReferenceId = callbackResponse.getInvoice_no();
            String txnStatus = callbackResponse.getTxn_status();
            Double txnAmount = Double.parseDouble(callbackResponse.getTxn_amount());

            PaymentOrder order = paymentOrdersRepository.findByUniquePaymentId(paymentReferenceId);

            if (order.getStatus().equals("PENDING")) {
                if (txnStatus.equals("00")) {
                    if (txnAmount.equals(order.getPaymentAmount())) {
                        try {
                            if (order.getClientTransactionId().startsWith("G")) {
                                OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(),
                                        "PAYMENT_CONFIRMED", "", callbackResponse.getMsg());
                            } else {
                                OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(),
                                        "PAYMENT_CONFIRMED", "", callbackResponse.getMsg());
                            }
                            String spErrorCode = txnStatus;
                            String statusDescription = callbackResponse.getMsg();
                            String paymentTransactionId = callbackResponse.getBp_lite_trx_id();
                            String clientTransactionId;
                            String status = "PAID";
                            PaymentOrder deliveryOrder = paymentOrdersRepository
                                    .findByUniquePaymentIdAndStatus(paymentReferenceId, "PENDING");
                            if (deliveryOrder != null) {
                                clientTransactionId = deliveryOrder.getClientTransactionId();
                                LogUtil.info(systemTransactionId, location,
                                        "PaymentOrder found. Update status and updated datetime", "");
                                deliveryOrder.setStatus(status);
                                deliveryOrder.setPaymentChannel(callbackResponse.getPay_method());
                                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                                deliveryOrder.setSpErrorCode(spErrorCode);
                                deliveryOrder.setSpOrderId(paymentTransactionId);
                                deliveryOrder.setStatusDescription(statusDescription);
                                paymentOrdersRepository.save(deliveryOrder);
                            } else {
                                LogUtil.info(systemTransactionId, location,
                                        "Payment Order not found for paymentTransactionId:" + paymentTransactionId, "");
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            LogUtil.info(systemTransactionId, location, "Error update Db:" + ex.getMessage(), "");
                        }
                        response.setSuccessStatus(HttpStatus.OK);
                        LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                        response.setSuccessStatus(HttpStatus.OK);
                        return ResponseEntity.status(response.getStatus()).body(response);
                    } else {
                        if (order.getClientTransactionId().startsWith("G")) {
                            OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(),
                                    "PAYMENT_FAILED", "", "Transaction Amount Does Not Match");
                        } else {
                            OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(),
                                    "PAYMENT_FAILED", "", "Transaction Amount Does Not Match");
                        }
                        response.setSuccessStatus(HttpStatus.OK);
                        return ResponseEntity.status(response.getStatus()).body(response);
                    }
                } else if (txnStatus.equals("09") || txnStatus.equals("97")) {
                    response.setSuccessStatus(HttpStatus.OK);
                    return ResponseEntity.status(response.getStatus()).body(response);
                } else {
                    if (order.getClientTransactionId().startsWith("G")) {
                        OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(),
                                "PAYMENT_FAILED", "", callbackResponse.getMsg());
                    } else {
                        OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_FAILED",
                                "", callbackResponse.getMsg());
                    }
                }
                response.setSuccessStatus(HttpStatus.OK);
            } else {
                response.setSuccessStatus(HttpStatus.OK);
                LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");

            }
        } catch (Exception ex) {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Exception ", ex.getMessage());
        }
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    public static String generateUniqueString() {
        UUID uuid = UUID.randomUUID();
        String uniqueString = uuid.toString().replaceAll("-", "");
        return uniqueString.substring(0, 14);
    }

    @Getter
    @Setter
    public static class BetterPayRequest {

        // private String customerId;
        private String customerName;
        private String phoneNo;
        private String email;
        private String creditCardNo;
        private String cardYear;
        private String cardMonth;
        private String cardCCV;
        private String paymentType;
        private String paymentService;
        private String transactionId;
        private Double orderTotalAmount;

    }

    @Getter
    @Setter
    public static class BetterPayResponse {

        private String paymentUrl;
        private String message;
    }

    @Getter
    @Setter
    public static class RequestPayment {

        private String orderId;
        private String storeName;
        private String storeId;
        private String regionCountryId;
        private String channel;
        private String paymentDescription;
        private Double paymentAmount;
        String paymentType;
        String storeVerticalCode;
        String browser;

    }

    @Getter
    @Setter
    public static class BNPLLIST {

        private String type;
        private String providerValue;
        private String providerName;
        private String logoUrl;
    }

    @Getter
    @Setter
    public static class PaymentDetails {

        private String customerId;
        private String invoiceId;
        private String status;
        private String storeName;
        private Double orderTotalAmount;

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


    @Getter
    @Setter
    public static class BetterPayCallbackResponse {

        @JsonProperty("bp_lite_trx_id")
        String bp_lite_trx_id;
        @JsonProperty("merchant_id")
        String merchant_id;
        @JsonProperty("invoice_no")
        String invoice_no;
        @JsonProperty("txn_status")
        String txn_status;
        @JsonProperty("msg")
        String msg;
        @JsonProperty("txn_amount")
        String txn_amount;
        @JsonProperty("pay_method")
        String pay_method;
        @JsonProperty("hash")
        String hash;

        public String toString() {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(this);
        }

    }


}
