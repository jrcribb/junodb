//  
//  Copyright 2023 PayPal Inc.
//  
//  Licensed to the Apache Software Foundation (ASF) under one or more
//  contributor license agreements.  See the NOTICE file distributed with
//  this work for additional information regarding copyright ownership.
//  The ASF licenses this file to You under the Apache License, Version 2.0
//  (the "License"); you may not use this file except in compliance with
//  the License.  You may obtain a copy of the License at
//  
//     http://www.apache.org/licenses/LICENSE-2.0
//  
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//  
package com.paypal.qa.juno;

import com.paypal.juno.client.JunoReactClient;
import com.paypal.juno.client.io.JunoRequest;
import com.paypal.juno.client.io.JunoResponse;
import com.paypal.juno.exception.JunoException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import reactor.core.publisher.Flux;

public class BatchReactSubscriber implements Subscriber<JunoResponse> {
    AtomicInteger num_records = new AtomicInteger(0);
    AtomicInteger count = new AtomicInteger(0);
    AtomicBoolean completed = new AtomicBoolean(false);
    List<JunoResponse> resp = Collections.synchronizedList(new ArrayList<JunoResponse>());
    BlockingQueue<JunoException> exception = new LinkedBlockingQueue<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchReactSubscriber.class);

    @BeforeClass
    public void setup() throws  IOException {
        
    }

    BatchReactSubscriber(int num) {
        num_records.set(num);
    }
    public BlockingQueue<JunoException> getAllException() {
        return exception;
    }

    public List<JunoResponse> getAllResps() {
        return resp;
    }

    public Throwable getException() {
        return exception.peek();
    }

    public List<JunoResponse> getResp() {
        return resp;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public int getCount() {
        return count.get();
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void  onComplete() {
        //if (getCount() )
        LOGGER.debug("========" + BatchReactSubscriber.class.getSimpleName() + " onCompleted: " + System.currentTimeMillis());
        completed.set(true);
    }

    @Override
    public void onError(Throwable e) {
        LOGGER.debug("========" + BatchReactSubscriber.class.getSimpleName() + " onError: " + e.toString());
        try {
            exception.put((JunoException)e);
        } catch (InterruptedException e1) {
            LOGGER.debug("exception put in onError gets exception" + e1);
        };
    }

    @Override
    public void onNext(JunoResponse message) {
        this.count.incrementAndGet();
        if (message == null) {
            LOGGER.debug("========" + BatchReactSubscriber.class.getSimpleName() + " No result");
            return;
        }
        try {
            resp.add(message);
        } catch (JunoException mex) {
            LOGGER.debug(mex.getMessage());
        }
        LOGGER.debug("========" + BatchReactSubscriber.class.getSimpleName() + " onNext: " + message.getStatus() + System.currentTimeMillis());
    }

    public static List<JunoResponse> getResponse(BatchReactSubscriber subscriber, int timeout) throws JunoException {
        boolean completed = subscriber.isCompleted();
        LOGGER.debug("complete is " + completed + " " + System.currentTimeMillis());
        int count = timeout / 10;
        while (!completed && count > 0) {
            Throwable t = subscriber.getException();
            if (t != null) {
                LOGGER.debug("=========== Got exception: " + t.getCause());
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                LOGGER.debug("thread sleep exception? " + e.getMessage() );
            }
            completed = subscriber.isCompleted();
            if (completed) {
                LOGGER.debug("=========== Completed");
            }
            count --;
            if (count == 0) {
                LOGGER.debug("=========== Timeout");
            }
            LOGGER.debug("inside of while loop time is " + System.currentTimeMillis());
        }
        return subscriber.getResp();
    }

    public static List<JunoResponse> subscriberResponse(Flux<JunoResponse> ob, int numKeys) throws JunoException {
        BatchReactSubscriber batchReactSubscriber = new BatchReactSubscriber(numKeys);

        LOGGER.debug("time before ob subscribe " + System.currentTimeMillis());
        ob.subscribe(batchReactSubscriber);
        LOGGER.debug("time after ob subscribe " + System.currentTimeMillis());

        BatchReactSubscriber.getResponse(batchReactSubscriber, 10000);
        if (batchReactSubscriber.isCompleted()) {
            LOGGER.debug("Batch complete");
            return batchReactSubscriber.getResp();
        }
        else {
            throw batchReactSubscriber.exception.poll();
        }
    }

    public static List<JunoResponse> async_dobatch(JunoReactClient asyncJunoClient, List<JunoRequest> requests) throws JunoException {
        Flux<JunoResponse> ob =  asyncJunoClient.doBatch(requests);
        return  subscriberResponse (ob, requests.size());
    }
}