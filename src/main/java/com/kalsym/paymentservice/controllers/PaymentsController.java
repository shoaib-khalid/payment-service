package com.kalsym.paymentservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.HttpResponse;
import com.kalsym.paymentservice.models.daos.*;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;


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

    @PostMapping(path = {"/makePayment"}, name = "payments-make-payment")
    public ResponseEntity<HttpResponse> makePayment(HttpServletRequest request, @Valid @RequestBody PaymentRequest paymentRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        Customer customer = customersRepository.getOne(paymentRequest.getCustomerId());

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
            paymentRequest.setStoreVerticalCode(storeDetails.getVerticalCode());
            paymentRequest.setPaymentAmount(res.getOrderGroupDetails().getTotal());
            LogUtil.info(systemTransactionId, location, "Payment Amount  ", paymentRequest.getPaymentAmount().toString());


        }

        paymentRequest.setEmail(customer.getEmail());
        paymentRequest.setPhoneNo(customer.getPhoneNumber());

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
    //FIXME: Check Where This API Using
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

    //CALLBACK HANDLING
    //SENANGPAY CALLBACK
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
            } else if (payment.get().getStatus().equals("pending")) {
                response.setSuccessStatus(HttpStatus.OK);
                return ResponseEntity.status(response.getStatus()).body(response);
            } else {
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

    //BetterPay
    //Callback
    @PostMapping(path = {"/request/callback"}, name = "payment-request-callback")
    public ResponseEntity<HttpResponse> paymentRequestCallback(HttpServletRequest request, @RequestBody Object requestBody) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        String systemTransactionId = StringUtility.CreateRefID("CB");
        LogUtil.info(systemTransactionId, location, "Request Callback Body ", requestBody.toString());

        String IP = request.getRemoteAddr();
        LogUtil.info(logprefix, location, "Get Provider List  : ", IP);

        JsonObject callbackResponse = new Gson().fromJson(requestBody.toString(), JsonObject.class);

        try {
            String paymentReferenceId = callbackResponse.get("invoice_no").getAsString();
            String txnStatus = callbackResponse.get("txn_status").getAsString();
            Double txnAmount = Double.parseDouble(callbackResponse.get("txn_amount").getAsString());

            PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(paymentReferenceId);

            if (order.getStatus().equals("PENDING")) {
                if (txnStatus.equals("00")) {
                    if (txnAmount.equals(order.getPaymentAmount())) {
                        if (order.getClientTransactionId().startsWith("G")) {
                            OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(), "PAYMENT_CONFIRMED", "", callbackResponse.get("msg").getAsString());
                        } else {
                            OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_CONFIRMED", "", callbackResponse.get("msg").getAsString());
                        }
                        String spErrorCode = txnStatus;
                        String statusDescription = callbackResponse.get("msg").getAsString();
                        String paymentTransactionId = callbackResponse.get("fpx_fpxTxnId").getAsString();
                        String clientTransactionId;
                        String status = "PAID";
                        PaymentOrder deliveryOrder = paymentOrdersRepository.findBySystemTransactionIdAndStatus(paymentReferenceId, "PENDING");
                        if (deliveryOrder != null) {
                            clientTransactionId = deliveryOrder.getClientTransactionId();
                            LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                            deliveryOrder.setStatus(status);
                            deliveryOrder.setPaymentChannel(callbackResponse.get("pay_method").getAsString());
                            deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                            deliveryOrder.setSpErrorCode(spErrorCode);
                            deliveryOrder.setSpOrderId(paymentTransactionId);
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
                } else if (txnStatus.equals("09") || txnStatus.equals("97")) {
                    response.setSuccessStatus(HttpStatus.OK);
                    return ResponseEntity.status(response.getStatus()).body(response);
                } else {
                    if (order.getClientTransactionId().startsWith("G")) {
                        OrderConfirm res = paymentService.groupOrderUpdateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "", callbackResponse.get("msg").getAsString());
                    } else {
                        OrderConfirm res = paymentService.updateStatus(order.getClientTransactionId(), "PAYMENT_FAILED", "", callbackResponse.get("msg").getAsString());
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


    @PostMapping(path = {"/payment-request"}, name = "payments-make-payment")
    public ResponseEntity<HttpResponse> betterPaymentReqeust(HttpServletRequest request, @Valid @RequestBody BetterPayRequest betterPayRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());

        Customer customer = customersRepository.getOne(betterPayRequest.getCustomerId());
        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(betterPayRequest.getTransactionId());


        //generate transaction id

        if (order.getClientTransactionId().startsWith("G")) {
            OrderGroup res = paymentService.getGroupOrder(order.getClientTransactionId());
            betterPayRequest.setOrderTotalAmount(res.getTotal());

        } else {
            OrderConfirm res = paymentService.getOrderById(order.getClientTransactionId());
            LogUtil.info(order.getSystemTransactionId(), location, "Order Service Return :   ", res.toString());
            StoreDetails storeDetails = paymentService.getStore(res.getStoreId());
            LogUtil.info(order.getSystemTransactionId(), location, "Payment Amount  ", res.getTotal().toString());
        }


        String token = "";//https://apipxyuat.apps.net.pk:8443/api/token
        String requestUrl = "https://www.demo.betterpay.me/merchant/api/v2/lite/direct/receiver";
        String callBackUrlBe = "https://api.symplified.it/payment-service/v1/payments/callback";
        String callBackUrlFeSuccess = "https://payment.dev-my.symplified.ai/thankyou/SUCCESS/ONLINEPAYMENT/Payment_was_successful/DELIVERIN";
        String callBackUrlFeFail = "https://payment.dev-my.symplified.ai/thankyou/SUCCESS/ONLINEPAYMENT/Payment_was_successful/DELIVERIN";
        String currency = "MYR";
        String merchantId = "10363";
        String desc = "TESTING";
        String bankCode = "CREDIT";
        String respondCode = "1";
        String skipReceipt = "0";

        RestTemplate restTemplate = new RestTemplate();

        JsonObject object = new JsonObject();
        object.addProperty("merchant_id", merchantId);
        object.addProperty("invoice", order.getSystemTransactionId());
        object.addProperty("amount", betterPayRequest.getOrderTotalAmount().toString());
        object.addProperty("payment_desc", desc); // will change
        object.addProperty("currency", currency);
        object.addProperty("buyer_name", customer.getName());
        object.addProperty("buyer_email", customer.getEmail());
        object.addProperty("phone", customer.getPhoneNumber());
        object.addProperty("callback_url_be", callBackUrlBe);
        object.addProperty("callback_url_fe_succ", callBackUrlFeSuccess);
        object.addProperty("callback_url_fe_fail", callBackUrlFeFail);
        object.addProperty("bank_code", bankCode);
        object.addProperty("respond", respondCode);
        object.addProperty("skip_receipt", skipReceipt);
        object.addProperty("card_number", betterPayRequest.getCreditCardNo());
        object.addProperty("card_year", betterPayRequest.getCardYear());
        object.addProperty("card_month", betterPayRequest.getCardMonth());
        object.addProperty("card_cvv", betterPayRequest.getCardCCV());

        String hmacHex = "";
        String message = betterPayRequest.getOrderTotalAmount() + bankCode + customer.getEmail() + customer.getName() + callBackUrlBe
                + callBackUrlFeFail + callBackUrlFeSuccess + betterPayRequest.getCardCCV() + betterPayRequest.getCardMonth()
                + betterPayRequest.getCreditCardNo() + betterPayRequest.getCardYear() + currency + order.getSystemTransactionId() + merchantId
                + desc + customer.getPhoneNumber() + respondCode + skipReceipt;
        System.err.println(message);

        String secret = "XPePraM9Lsgz";
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
            LogUtil.info(order.getSystemTransactionId(), location, "Better Pay HMAC Exception  ", e.getMessage());

        }
        object.addProperty("hash", hmacHex);
        System.err.println(object.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> data = new HttpEntity<>(object.toString(), headers);
        System.err.println("url for orderDetails" + requestUrl);
        try {
            ResponseEntity<String> responses = restTemplate.exchange(requestUrl, HttpMethod.POST, data, String.class);

            int statusCode = responses.getStatusCode().value();
            LogUtil.info(logprefix, location, "Responses", responses.getBody());
            if (statusCode == 200) {
                LogUtil.info(logprefix, location, "Get Token: " + responses.getBody(), "");

                JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                token = jsonResp.get("ACCESS_TOKEN").getAsString();

            } else {
                LogUtil.info(logprefix, location, "Request failed", responses.getBody());
                token = "";
            }
        } catch (Exception exception) {
            LogUtil.info(logprefix, location, "Exception : ", exception.getMessage());
            token = "";

        }


        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping(path = {"/getPaymentDetails/{invoiceId}"}, name = "payments-get-details")
    public ResponseEntity<HttpResponse> betterPaymentReqeust(HttpServletRequest request, @PathVariable(name = "invoiceId") String invoiceId) throws ParseException {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpResponse response = new HttpResponse(request.getRequestURI());
        PaymentDetails paymentDetails = new PaymentDetails();

        PaymentOrder order = paymentOrdersRepository.findBySystemTransactionId(invoiceId);

        Date current = new Date();

        // Parse the string date into a Date object
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = dateFormat.parse(order.getCreatedDate());

        // Find the difference between the current date and the string date in minutes
        long diff = (current.getTime() - date.getTime()) / (1000 * 60);
        Order storeOrder = orderRepository.getOne(order.getClientTransactionId());

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
        return ResponseEntity.status(response.getStatus()).body(response);

    }


    @Getter
    @Setter
    public static class BetterPayRequest {

        private String customerId;
        private String creditCardNo;
        private String cardYear;
        private String cardMonth;
        private String cardCCV;
        private String paymentType;
        private String transactionId;
        private Double orderTotalAmount;

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

}
