/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider;

import java.util.concurrent.CountDownLatch;

/**
 *
 * @author Ali Khan
 */
public abstract class DispatchRequest implements Runnable {

//    private final String requestXml;
    private ProcessResult responseResult;
    public CountDownLatch synchronizeLatch;
    
    protected DispatchRequest(CountDownLatch latch) {
        this.synchronizeLatch = latch;
    }

    /**
     * Process the request
     */
    @Override
    public void run() {       
        //TODO: Save request in transaction table
        //insertTransactionInDB();
        responseResult = process();
        finish();
    }

    /**
     * Returns the responseXml
     *
     * @return
     */
    public abstract ProcessResult process();

    /**
     * This method is called to free the awaiting threads, if any
     */
    private void finish() {
        synchronizeLatch.countDown();
    }

    public ProcessResult getProcessResult() {
        return responseResult;
    }

}

