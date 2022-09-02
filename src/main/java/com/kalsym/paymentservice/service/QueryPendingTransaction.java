package com.kalsym.paymentservice.service;

import com.kalsym.paymentservice.controllers.PaymentsController;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.repositories.PaymentOrdersRepository;
import com.kalsym.paymentservice.utils.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.xml.ws.soap.Addressing;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class QueryPendingTransaction {

    @Autowired
    PaymentOrdersRepository paymentOrdersRepository;

    @Autowired
    PaymentsController service;

    @Scheduled(cron = "${payment-service:0 0/05 * * * ?}")
    public void QueryPendingTransaction() throws ParseException {
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();

        LogUtil.info("QueryPendingTXN", location, "QUERY PENDING TXN", "");
        List<String> status = new ArrayList<>();

        List<PaymentOrder> paymentOrders = paymentOrdersRepository.findAllByStatus("PENDING");
        for (PaymentOrder order : paymentOrders) {

            long currentTimestamp = System.currentTimeMillis();

            Date parsedDate = null;
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                parsedDate = dateFormat.parse(order.getCreatedDate());
//                System.err.println(" Parse Date : " + parsedDate);
                Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
            } catch (Exception e) { //this generic but you can control another types of exception
                // look the origin of excption

            }
            long searchTimestamp = parsedDate.getTime();// this also gives me back timestamp in 13 digit (1425506040493)


            long difference = Math.abs(currentTimestamp - searchTimestamp);

            if (difference < 5 * 60 * 1000) {
                LogUtil.info("QueryPendingTXN", location, "Order Id : " + order.getClientTransactionId(), "");
                service.queryOrderStatus(order.getClientTransactionId());
                LogUtil.info("QueryPendingTXN Status ", location, "Order Id : " + order.getClientTransactionId() + " Order Status : " + order.getStatus(), "");
            }
        }
    }

}
