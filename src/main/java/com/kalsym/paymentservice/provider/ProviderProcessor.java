/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider;

import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.models.daos.Provider;
import com.kalsym.paymentservice.utils.LogUtil;
import com.kalsym.paymentservice.controllers.ProcessRequest;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;

/**
 *
 * @author taufik
 */
public class ProviderProcessor {
        
        private final String sysTransactionId;
        private final Provider provider;
        private final PaymentRequest order;
        private final String spOrderId;
        private final HashMap providerConfig;
        private ProcessRequest caller;
        private String functionName;
        private Object requestBody;
        
        public ProviderProcessor(ProcessRequest caller, String sysTransactionId, 
                            Provider provider, HashMap providerConfig, PaymentRequest order, String functionName){
            this.sysTransactionId = sysTransactionId;
            this.provider=provider;
            this.order=order;
            this.providerConfig = providerConfig;
            this.caller = caller;
            this.functionName=functionName;
            this.spOrderId = null;
        }
        
        public ProviderProcessor(ProcessRequest caller, String sysTransactionId, 
                            Provider provider, HashMap providerConfig, String spOrderId, String functionName){
            this.sysTransactionId = sysTransactionId;
            this.provider=provider;
            this.spOrderId=spOrderId;
            this.providerConfig = providerConfig;
            this.caller = caller;
            this.functionName=functionName;
            this.order=null;
        }
        
        public ProviderProcessor(ProcessRequest caller, String sysTransactionId, 
                            Provider provider, HashMap providerConfig, Object requestBody, String functionName){
            this.sysTransactionId = sysTransactionId;
            this.provider=provider;
            this.requestBody=requestBody;
            this.providerConfig = providerConfig;
            this.caller = caller;
            this.functionName=functionName;
            this.spOrderId = null;
            this.order=null;
        }
        
        /* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public ProcessResult startProcess() {
            String logprefix = sysTransactionId;
            String location = "ProviderThread";
                
            try {
                
                //get the java class name from SPId->JavaClass mapping
                String className="";
                LogUtil.info(logprefix, location, "functionName:"+functionName, "");
                if (functionName.equalsIgnoreCase("MakePayment")) {
                    className=provider.getMakePaymentClassName();
                    LogUtil.info(logprefix, location, "MakePaymentClassname for SP ID:"+provider.getId()+" -> "+className, "");
                } else if (functionName.equalsIgnoreCase("SpCallback")) {
                    className=provider.getSpCallbackClassName();
                    LogUtil.info(logprefix, location, "SpCallbackClassname for SP ID:"+provider.getId()+" -> "+className, "");
                }
                Class classObject = Class.forName(className);
                DispatchRequest reqFactoryObj=null;   
                CountDownLatch latch = new CountDownLatch(1);
                
                //get all constructors
                Constructor<?> cons[] = classObject.getConstructors();
                LogUtil.info(logprefix, location, "Constructors:"+cons[0].toString(), "");
                try {       
                        if (functionName.equalsIgnoreCase("MakePayment")) {
                            reqFactoryObj = (DispatchRequest) cons[0].newInstance(latch, providerConfig, order, this.sysTransactionId,provider.getId());
                        } else if (functionName.equalsIgnoreCase("SpCallback")) {
                            reqFactoryObj = (DispatchRequest) cons[0].newInstance(latch, providerConfig, this.requestBody, this.sysTransactionId);                        
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                }

                LogUtil.info(logprefix, location, "Forking a new thread", "");
                
                Thread tReqFactory = new Thread(reqFactoryObj);
                tReqFactory.start();

                try {
                    //wait until latch countdown happen
                    latch.await(2, TimeUnit.MINUTES);
                } catch (Exception exp) {
                    LogUtil.error(logprefix, location, "Error in awaiting", "", exp);
                }
                
                LogUtil.info(logprefix, location, "ProviderThread finish", "");
                ProcessResult spResponse = reqFactoryObj.getProcessResult();
                LogUtil.info(logprefix, location, "Response code:" + spResponse.resultCode+" string:"+spResponse.resultString+" returnObject:"+spResponse.returnObject, "");
                
                ProcessResult response = new ProcessResult();
                if (functionName.equalsIgnoreCase("MakePayment") && spResponse.resultCode==0) {
                    MakePaymentResult paymentResult = (MakePaymentResult)spResponse.returnObject;
                    if (paymentResult.isSuccess) {
                        response.isSuccess=true;
                        response.returnObject = paymentResult;
                    } else {
                        response.isSuccess=false;
                    }
                } else if (functionName.equalsIgnoreCase("SpCallback") && spResponse.resultCode==0) {
                    SpCallbackResult callbackResult = (SpCallbackResult)spResponse.returnObject;
                    response.returnObject = callbackResult;                    
                }
                
                
                return response;                
           } catch (Exception exp) {
                LogUtil.error(logprefix, location, "Error in awaiting. Will not continue with other SP ", "", exp);
                ProcessResult response = new ProcessResult();
                response.resultCode=-1;
                return response;
           }
        }
}
