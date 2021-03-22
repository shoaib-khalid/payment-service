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
public class SyncDispatcher extends DispatchRequest {

    public SyncDispatcher(CountDownLatch latch) {
        super(latch);
    }

    @Override
    public ProcessResult process() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}

