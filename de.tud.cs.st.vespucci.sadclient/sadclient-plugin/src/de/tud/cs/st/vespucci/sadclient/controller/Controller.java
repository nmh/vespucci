/**
 *  License (BSD Style License):
 *   Copyright (c) 2012
 *   Software Engineering
 *   Department of Computer Science
 *   Technische Universität Darmstadt
 *   All rights reserved.
 * 
 *   Redistribution and use in source and binary forms, with or without
 *   modification, are permitted provided that the following conditions are met:
 * 
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the Software Engineering Group or Technische 
 *     Universität Darmstadt nor the names of its contributors may be used to 
 *     endorse or promote products derived from this software without specific 
 *     prior written permission.
 * 
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *   AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *   IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *   ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *   LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *   CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *   SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *   INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *   CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *   ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE.
 * 
 *
 * $Id$
 */
package de.tud.cs.st.vespucci.sadclient.controller;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.core.runtime.IProgressMonitor;

import de.tud.cs.st.vespucci.sadclient.concurrent.Callback;
import de.tud.cs.st.vespucci.sadclient.concurrent.RunnableWithCallback;
import de.tud.cs.st.vespucci.sadclient.model.SAD;
import de.tud.cs.st.vespucci.sadclient.model.SADClient;
import de.tud.cs.st.vespucci.sadclient.model.SADClientException;

/**
 * The me Responsible for starting computations in a separate thread.
 * 
 * @author Mateusz Parzonka
 * 
 */
public class Controller {

    final private static Controller instance = new Controller();
    final private SADClient sadClient;
    private ExecutorService pool;
    private Future<SAD[]> sadCollectionFuture;

    private Controller() {
	pool = Executors.newFixedThreadPool(4);
	sadClient = new SADClient();
    }

    public static Controller getInstance() {
	return instance;
    }

    /**
     * Called by the activator.
     */
    public void start() {
	pool = Executors.newFixedThreadPool(2);
    }

    /**
     * Called by the activator.
     */
    public void stop() {
	pool.shutdown();
    }

    /**
     * Returns
     * 
     * @param shell
     * @return
     * @throws Exception
     * @throws InterruptedException
     * @throws SADClientException
     */
    public SAD[] getSADCollection() {
	getSADCollectionFromServer();
	try {
	    return sadCollectionFuture.get();
	} catch (Exception e) {
	    throw new SADClientException(e);
	}
    }

    /**
     * Executes a working thread getting the SADs from the server.
     */
    public void getSADCollectionFromServer() {
	// TODO is it wise to cancel a running future before?
	sadCollectionFuture = pool.submit(new Callable<SAD[]>() {
	    @Override
	    public SAD[] call() throws Exception {
		return sadClient.getSADCollection();
	    }
	});
    }

    public void getSAD(final String id, Callback<SAD> callback) {
	System.out.println("Calling getSAD with id " + id + " and callback " + callback);
	pool.execute(new RunnableWithCallback<SAD>(new Callable<SAD>() {
	    @Override
	    public SAD call() throws Exception {
		return sadClient.getSAD(id);
	    }
	}, callback));
    }

    public void saveDescription(final String id, final String name, final String type, final String abstrct,
	    final Callback<SAD> callback) {
	System.out.println("Calling saveDescription with id " + id + " and callback " + callback);

	// getSADCollectionFromServer();
	pool.execute(new RunnableWithCallback<SAD>(new Callable<SAD>() {
	    @Override
	    public SAD call() throws Exception {
		sadClient.putSAD(id, name, type, abstrct);
		return sadClient.getSAD(id);
	    }
	}, callback));
    }

    // Model

    public void downloadModel(final String id, final File downloadLocation, final IProgressMonitor progressMonitor) {
	pool.execute(new Runnable() {
	    @Override
	    public void run() {
		sadClient.getModel(id, downloadLocation, progressMonitor);
	    }
	});
    }

    /**
     * Uploads the model with the specified id, keeps updating the progress
     * handler and finally updates the view via given callback.
     * 
     * @param id
     * @param uploadFile
     * @param callback
     * @param progressMonitor
     */
    public void uploadModel(final String id, final File uploadFile, final Callback<SAD> callback,
	    final IProgressMonitor progressMonitor) {
	pool.execute(new RunnableWithCallback<SAD>(new Callable<SAD>() {
	    @Override
	    public SAD call() throws Exception {
		sadClient.putModel(id, uploadFile, progressMonitor);
		return sadClient.getSAD(id);
	    }
	}, callback));
    }

    /**
     * Deletes the model of the SAD and updates the view which called the
     * operation (non-blocking).
     * 
     * @param id
     * @param file
     * @param callback
     */
    public void deleteModel(final String id, final Callback<SAD> callback) {
	pool.execute(new RunnableWithCallback<SAD>(new Callable<SAD>() {
	    @Override
	    public SAD call() throws Exception {
		sadClient.deleteModel(id);
		return sadClient.getSAD(id);
	    }
	}, callback));
    }

    // Documentation

    public void downloadDocumentation(final String id, final File downloadLocation, final IProgressMonitor progressMonitor) {
	pool.execute(new Runnable() {
	    @Override
	    public void run() {
		sadClient.getDocumentation(id, downloadLocation, progressMonitor);
	    }
	});
    }

    /**
     * Uploads the documentation with the specified id, keeps updating the
     * progress handler and finally updates the view via given callback.
     * 
     * @param id
     * @param uploadFile
     * @param callback
     * @param progressMonitor
     */
    public void uploadDocumentation(final String id, final File uploadFile, final Callback<SAD> callback,
	    final IProgressMonitor progressMonitor) {
	pool.execute(new RunnableWithCallback<SAD>(new Callable<SAD>() {
	    @Override
	    public SAD call() throws Exception {
		sadClient.putDocumentation(id, uploadFile, progressMonitor);
		return sadClient.getSAD(id);
	    }
	}, callback));
    }

    /**
     * Deletes the documentation of the SAD and updates the view which called
     * the operation (non-blocking).
     * 
     * @param id
     * @param file
     * @param callback
     */
    public void deleteDocumentation(final String id, final Callback<SAD> callback) {
	pool.execute(new RunnableWithCallback<SAD>(new Callable<SAD>() {
	    @Override
	    public SAD call() throws Exception {
		sadClient.deleteDocumentation(id);
		return sadClient.getSAD(id);
	    }
	}, callback));
    }

}