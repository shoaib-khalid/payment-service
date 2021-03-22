/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.controllers;

import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.Provider;
import com.kalsym.paymentservice.models.daos.ProviderRatePlanId;
import com.kalsym.paymentservice.models.daos.ProviderRatePlan;
import com.kalsym.paymentservice.models.daos.ProviderConfiguration;
import com.kalsym.paymentservice.models.daos.ProviderIp;
import com.kalsym.paymentservice.repositories.ProviderRatePlanRepository;
import com.kalsym.paymentservice.repositories.ProviderConfigurationRepository;
import com.kalsym.paymentservice.repositories.ProviderRepository;
import com.kalsym.paymentservice.repositories.ProviderIpRepository;
import com.kalsym.paymentservice.provider.ProviderProcessor;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.QueryPaymentResult;
import com.kalsym.paymentservice.provider.SpCallbackResult;
import com.kalsym.paymentservice.provider.ProviderResult;
import com.kalsym.paymentservice.utils.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;   

import com.google.gson.JsonObject;

/**
 *
 * @author user
 */
public class ProcessRequest {
    PaymentRequest order;
    PaymentOrder deliveryOrder;
    String sysTransactionId;
    String logprefix;
    String location;    
    ProviderRatePlanRepository providerRatePlanRepository;    
    ProviderConfigurationRepository providerConfigurationRepository;
    ProviderRepository providerRepository;
    int providerThreadRunning;
    MakePaymentResult submitOrderResult;
    QueryPaymentResult queryOrderResult;
    SpCallbackResult spCallbackResult;
    Object requestBody;
    
    public ProcessRequest(String sysTransactionId, PaymentRequest order, ProviderRatePlanRepository providerRatePlanRepository, 
            ProviderConfigurationRepository providerConfigurationRepository, ProviderRepository providerRepository) {
        this.sysTransactionId = sysTransactionId;
        this.order = order;
        this.logprefix = sysTransactionId;
        this.location = "ProcessRequest";
        this.providerRatePlanRepository = providerRatePlanRepository;
        this.providerConfigurationRepository = providerConfigurationRepository;
        this.providerRepository = providerRepository;         
    }
    
    public ProcessRequest(String sysTransactionId, PaymentOrder deliveryOrder, ProviderRatePlanRepository providerRatePlanRepository, 
            ProviderConfigurationRepository providerConfigurationRepository, ProviderRepository providerRepository) {
        this.sysTransactionId = sysTransactionId;
        this.deliveryOrder = deliveryOrder;
        this.logprefix = sysTransactionId;
        this.location = "ProcessRequest";
        this.providerRatePlanRepository = providerRatePlanRepository;
        this.providerConfigurationRepository = providerConfigurationRepository;
        this.providerRepository = providerRepository;
    }
    
    public ProcessRequest(String sysTransactionId, Object requestBody, ProviderRatePlanRepository providerRatePlanRepository, 
            ProviderConfigurationRepository providerConfigurationRepository, ProviderRepository providerRepository) {
        this.sysTransactionId = sysTransactionId;
        this.requestBody = requestBody;
        this.logprefix = sysTransactionId;
        this.location = "ProcessRequest";
        this.providerRatePlanRepository = providerRatePlanRepository;
        this.providerConfigurationRepository = providerConfigurationRepository;
        this.providerRepository = providerRepository;
    }
   
    public ProcessResult MakePayment() {        
        //get provider rate plan  
        LogUtil.info(logprefix, location, "Find provider rate plan for productCode:"+order.getProductCode(), "");
        List<ProviderRatePlan> providerRatePlanList = providerRatePlanRepository.findByIdProductCode(order.getProductCode());
        
        ProcessResult result = new ProcessResult();
        for (int i=0;i<providerRatePlanList.size();i++) {
            //try every provider                       
            LogUtil.info(logprefix, location, "ProviderId:"+providerRatePlanList.get(i).getProvider().getId()+" productCode:"+order.getProductCode(), "");
            Optional<Provider> provider = providerRepository.findById(providerRatePlanList.get(i).getProvider().getId());
            List<ProviderConfiguration> providerConfigList = providerConfigurationRepository.findByIdSpId(providerRatePlanList.get(i).getProvider().getId());
            HashMap config = new HashMap();
            for (int j=0;j<providerConfigList.size();j++) {
                String fieldName = providerConfigList.get(j).getId().getConfigField();
                String fieldValue = providerConfigList.get(j).getConfigValue();
                config.put(fieldName, fieldValue);
            }
            ProviderProcessor processor = new ProviderProcessor(this, sysTransactionId, provider.get(), config, order, "MakePayment");
            result = processor.startProcess();
            
            LogUtil.info(logprefix, location, "ProviderId:"+providerRatePlanList.get(i).getProvider().getId()+" Result isSuccess:"+result.isSuccess+" resultCode:"+result.resultCode+" returnObject:"+result.returnObject, "");
            
            //attempt next provider if fail
            if (result.isSuccess==true) {
                break;
            }
        }
        
        LogUtil.info(logprefix, location, "SubmitOrder finish. isSuccess:"+result.isSuccess+" resultCode:"+result.resultCode, " returnObject:"+result.returnObject);
        return result;
    }
    
    
    /*public ProcessResult QueryPayment() {        
        //get provider rate plan  
        LogUtil.info(logprefix, location, "ProviderId:"+deliveryOrder.getDeliveryProviderId()+" productCode:"+order.getProductCode(), "");
        Optional<Provider> provider = providerRepository.findById(deliveryOrder.getDeliveryProviderId());
        List<ProviderConfiguration> providerConfigList = providerConfigurationRepository.findByIdSpId(deliveryOrder.getDeliveryProviderId());
        HashMap config = new HashMap();
        for (int j=0;j<providerConfigList.size();j++) {
            String fieldName = providerConfigList.get(j).getId().getConfigField();
            String fieldValue = providerConfigList.get(j).getConfigValue();
            config.put(fieldName, fieldValue);
        }
        ProviderThread dthread = new ProviderThread(this, sysTransactionId, provider.get(), config, order, "QueryOrder");
        dthread.start();

        try {
            Thread.sleep(100);
        } catch (Exception ex) {}
        
        while (providerThreadRunning>0) {
            try {
                Thread.sleep(500);
            } catch (Exception ex) {}
            //LogUtil.info(logprefix, location, "Current ProviderThread running:"+providerThreadRunning, "");
        }
        
        ProcessResult response = new ProcessResult();
        response.resultCode=0;
        response.returnObject=queryOrderResult;
        LogUtil.info(logprefix, location, "SubmitOrder finish. resultCode:"+response.resultCode, " queryOrderResult:"+queryOrderResult);
        return response;
    }*/
    
    public ProcessResult ProcessCallback(String spIP, ProviderIpRepository providerIpRepository) {        
        //get provider rate plan  
        LogUtil.info(logprefix, location, "Caller IP:"+spIP, "");
        //get provider based on IP
        Optional<ProviderIp> spId = providerIpRepository.findById(spIP);
        
        ProcessResult result = new ProcessResult();
        //if (spId.isPresent()) {
            //int providerId = spId.get().getSpId();
            int providerId = 1;
            LogUtil.info(logprefix, location, "Provider found. SpId:"+providerId, "");
            Optional<Provider> provider = providerRepository.findById(providerId);
            List<ProviderConfiguration> providerConfigList = providerConfigurationRepository.findByIdSpId(providerId);
            HashMap config = new HashMap();
            for (int j=0;j<providerConfigList.size();j++) {
                String fieldName = providerConfigList.get(j).getId().getConfigField();
                String fieldValue = providerConfigList.get(j).getConfigValue();
                config.put(fieldName, fieldValue);
            }
            
            ProviderProcessor processor = new ProviderProcessor(this, sysTransactionId, provider.get(), config, this.requestBody, "SpCallback");
            result = processor.startProcess();
            
            LogUtil.info(logprefix, location, "ProviderId:"+providerId+" Result isSuccess:"+result.isSuccess+" resultCode:"+result.resultCode, "");
            
           

        LogUtil.info(logprefix, location, "ProcessCallback finish. resultCode:"+result.resultCode, " spCallbackResult:"+result.returnObject);
        return result;
    }
   
}
