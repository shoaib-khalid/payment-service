package com.kalsym.paymentservice.controllers;

import com.kalsym.paymentservice.models.HttpReponse;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.repositories.*;
import com.kalsym.paymentservice.service.OrderPaymentService;
import com.kalsym.paymentservice.service.Response.OrderConfirm;
import com.kalsym.paymentservice.service.Response.StoreDetails;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import com.kalsym.paymentservice.utils.StringUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

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

    @PostMapping(path = {"/makePayment"}, name = "payments-make-payment")
    public ResponseEntity<HttpReponse> makePayment(HttpServletRequest request,
                                                   @Valid @RequestBody PaymentRequest paymentRequest) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "", "");

        //generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("PY");
        paymentRequest.setSystemTransactionId(systemTransactionId);

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
   /* @PostMapping(path = {"/callback"}, name = "payments-sp-callback")
    public ResponseEntity<HttpReponse> spCallback(HttpServletRequest request,
                                                  @RequestParam Map<String, String> requestBody,
                                                  @RequestParam(required = false, defaultValue = "") String name,
                                                  @RequestParam(required = false, defaultValue = "") String email,
                                                  @RequestParam(required = false, defaultValue = "") String phone,
                                                  @RequestParam(required = false, defaultValue = "") String amount,
                                                  @RequestParam(required = false, defaultValue = "") String hash,
                                                  @RequestParam(required = false, defaultValue = "") int status_id,
                                                  @RequestParam(required = false, defaultValue = "") String order_id,
                                                  @RequestParam(required = false, defaultValue = "") String transaction_id,
                                                  @RequestParam(required = false, defaultValue = "") String msg) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());
        if (requestBody.isEmpty()) {
            requestBody.put("name", name);
            requestBody.put("email", email);
            requestBody.put("phone", phone);
            requestBody.put("amount", amount);
            requestBody.put("hash", hash);
            requestBody.put("status_id", String.valueOf(status_id));
            requestBody.put("order_id", order_id);
            requestBody.put("transaction_id", transaction_id);
            requestBody.put("msg", msg);
        }

        LogUtil.info(logprefix, location, "receive callback from Provider", "");
        // using for-each loop for iteration over Map.entrySet()
        // Gson gson = new Gson();
        // JsonObject requestBodyJson = gson.toJsonTree(requestBody).getAsJsonObject();
        for (Map.Entry<String, String> entry : requestBody.entrySet()) {
            LogUtil.info(logprefix, location, "Key = " + entry.getKey() + ", Value = " + entry.getValue(), "");
        }
        //generate transaction id
        String systemTransactionId = StringUtility.CreateRefID("CB");
        String IP = request.getRemoteAddr();

        PaymentOrder order = paymentOrdersRepository.findByClientTransactionId(order_id);
        LogUtil.info(logprefix, location, "IP:" + IP, order.getClientTransactionId());
        LogUtil.info(logprefix, location, "IP:" + IP, order.getClientTransactionId());
        LogUtil.info(logprefix, location, "IP:" + IP, providerIpRepository.toString());
        ProcessRequest process = new ProcessRequest(systemTransactionId, requestBody, providerRatePlanRepository, providerConfigurationRepository, providerRepository);
        ProcessResult processResult = process.ProcessCallback(IP, providerIpRepository, order.getSpId());
        LogUtil.info(systemTransactionId, location, "ProcessRequest finish. resultCode:" + processResult.resultCode, "");

        if (processResult.resultCode == 0) {
            //update order status in db
            SpCallbackResult spCallbackResult = (SpCallbackResult) processResult.returnObject;
            String spOrderId = spCallbackResult.spOrderId;
            String status = spCallbackResult.status;
            int spId = spCallbackResult.providerId;
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
//            https://cinema-online.symplified.store/thankyou?txid=1623334556642263859&refId=6c9869ec-b533-4774-8c48-b7d293d7ac6a&status=SUCCESS

            try {
                //send redirect to Thank You page
                String url = "https://cinema-online.symplified.store/thankyou?txid=" + paymentTransactionId + "&refId=" + clientTransactionId + "&status=" + status;
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
            //fail to get price
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }*/

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
                OrderConfirm res = paymentService.updateStatus(order_id, "FAILED", "", msg);

                //fail to get price
                return "<html>\n" + "OK" + "\n" + "</html>";
            }
        } else {
            response.setSuccessStatus(HttpStatus.OK);
            LogUtil.info(systemTransactionId, location, "Order Status is " + order.getStatus(), "");
            return "<html>\n" + "OK" + "\n" + "</html>";

        }

    }

/*    @GetMapping(path = {"/return"}, name = "payments-sp-callback-senangPay")
    public ResponseEntity<HttpReponse> returnSP(HttpServletRequest request,
                                                @RequestParam(required = false, defaultValue = "") String name,
                                                @RequestParam(required = false, defaultValue = "") String email,
                                                @RequestParam(required = false, defaultValue = "") String phone,

                                                @RequestParam(required = false, defaultValue = "") String amount,
                                                @RequestParam(required = false, defaultValue = "") String hash,
                                                @RequestParam(required = false, defaultValue = "") int status_id,
                                                @RequestParam(required = false, defaultValue = "") String order_id,
                                                @RequestParam(required = false, defaultValue = "") String transaction_id,
                                                @RequestParam(required = false, defaultValue = "") String msg) {
        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        HttpReponse response = new HttpReponse(request.getRequestURI());

        LogUtil.info(logprefix, location, "Receive returnUrl ", "Name: " + name + " email: " + email + " phone: " + phone + " amount:" + amount + " hash :" + hash + " orderId: " + order_id + " transactionId: " + transaction_id + " msg: " + msg);
        // using for-each loop for iteration over Map.entrySet()
        ///Gson gson = new Gson();
        ///JsonObject requestBodyJson = gson.toJsonTree(requestBody).getAsJsonObject();
//        for (Map.Entry<String, String> entry : requestBody.entrySet()) {
//            LogUtil.info(logprefix, location, "Key = " + entry.getKey() + ", Value = " + entry.getValue(), "");
//        }
        //generate transaction id
//        OrderConfirm res = paymentService.updateStatus(order_id, "SUCCESS", "KUMAR", msg);
//        System.out.println("Return response " + res.toString());
        String systemTransactionId = StringUtility.CreateRefID("CB");
        String IP = request.getRemoteAddr();
        if (status_id == 1) {
            OrderConfirm res = paymentService.updateStatus(order_id, "PAYMENT_CONFIRMED", "", msg);
            StoreDetails stores = paymentService.getStoreDeliveryDetails(res.getStoreId());


            String spErrorCode = String.valueOf(status_id);
            String statusDescription = msg;
            String paymentTransactionId = transaction_id;
            String clientTransactionId = order_id;
            String status = "SUCCESS";
            PaymentOrder deliveryOrder = paymentOrdersRepository.findByClientTransactionIdAndStatus(order_id, null);
            if (deliveryOrder != null) {
                clientTransactionId = deliveryOrder.getClientTransactionId();
                LogUtil.info(systemTransactionId, location, "DeliveryOrder found. Update status and updated datetime", "");
                deliveryOrder.setStatus(status);
                deliveryOrder.setUpdatedDate(DateTimeUtil.currentTimestamp());
                deliveryOrder.setSpErrorCode(spErrorCode);
                deliveryOrder.setSpOrderId(transaction_id);
                deliveryOrder.setStatusDescription(statusDescription);
                paymentOrdersRepository.save(deliveryOrder);
            } else {
                LogUtil.info(systemTransactionId, location, "DeliveryOrder not found for paymentTransactionId:" + paymentTransactionId, "");
            }

            try {
                //send redirect to Thank You page
                String url = "https://" + stores.getDomain() + ".symplified.store/thankyou?txid=" + paymentTransactionId + "&refId=" + clientTransactionId + "&status=" + status;
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
//                response.setData(processResult.returnObject);
                LogUtil.info(systemTransactionId, location, "Response with " + HttpStatus.OK, "");
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        } else {
            OrderConfirm res = paymentService.updateStatus(order_id, "FAILED", "", msg);

            //fail to get price
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }*/


}
